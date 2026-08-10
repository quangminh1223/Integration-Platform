package msb.com.vn.qrservice.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.logging.model.TransactionLog;
import msb.com.vn.qrservice.queue.ManagedQueue;
import msb.com.vn.qrservice.queue.QueueManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service ghi log giao dịch ASYNC — dùng QueueManager để quản lý queue tập trung.
 *
 * Business thread chỉ serialize + offer vào ManagedQueue → trả về ngay (< 1ms).
 * QueueManager quản lý lifecycle + metrics của queue này.
 */
@Slf4j
@Service
public class TransactionLogService {

    public static final String QUEUE_NAME = "transaction-log";

    private static final org.slf4j.Logger TX_LOG =
            org.slf4j.LoggerFactory.getLogger("TRANSACTION_LOG");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final DateTimeFormatter REQ_ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final AtomicLong SEQ = new AtomicLong(1);

    private final QueueManager queueManager;
    private final DeadLetterHandler deadLetterHandler;
    private ManagedQueue<String> logQueue;

    @Value("${spring.application.name:qr-service}")
    private String appName;

    @Value("${transaction-log.index:esb_cms_log}")
    private String logIndex;

    @Value("${transaction-log.provider-code:#{null}}")
    private String defaultProviderCode;

    @Value("${transaction-log.queue-capacity:10000}")
    private int queueCapacity;

    @Value("${transaction-log.writer-threads:2}")
    private int writerThreads;

    @Value("${transaction-log.batch-size:100}")
    private int batchSize;

    @Value("${transaction-log.ttl-millis:180000}")
    private long ttlMillis;

    public TransactionLogService(QueueManager queueManager, DeadLetterHandler deadLetterHandler) {
        this.queueManager = queueManager;
        this.deadLetterHandler = deadLetterHandler;
    }

    @PostConstruct
    public void init() {
        // Đăng ký queue với TTL 3 phút + DLQ handler
        logQueue = queueManager.register(QUEUE_NAME, queueCapacity, writerThreads, batchSize,
                ttlMillis,
                TX_LOG::info,
                deadLetterHandler::handle);
        log.info("TransactionLogService registered queue [{}] với TTL={}ms", QUEUE_NAME, ttlMillis);
    }

    // ─── Public API (non-blocking) ────────────────────────────────────────────

    public void logTransaction(String apiName, String spfUrl,
                                Object requestBody, Object responseBody,
                                String respCode, String respDesc, String status,
                                LocalDateTime receivedAt, LocalDateTime processedAt) {

        String logJson = buildLogJson(apiName, spfUrl, requestBody, responseBody,
                respCode, respDesc, status, receivedAt, processedAt);
        logQueue.offer(logJson);
    }

    public void logTransaction(String apiName, String spfUrl,
                                Object requestBody, Object responseBody,
                                boolean success) {
        LocalDateTime now = LocalDateTime.now();
        logTransaction(apiName, spfUrl, requestBody, responseBody,
                success ? appName + ".0" : appName + ".ERR",
                success ? "Transaction is successfull!" : "Transaction failed",
                success ? "4" : "5", now, now);
    }

    public String getMetrics() {
        ManagedQueue.QueueStats s = logQueue.getStats();
        return String.format("queue=%d/%d, processed=%d, dropped=%d",
                s.currentSize(), s.capacity(), s.processed(), s.dropped());
    }

    // ─── Build log JSON ─────────────────────────────────────────────────────

    private String buildLogJson(String apiName, String spfUrl,
                                 Object requestBody, Object responseBody,
                                 String respCode, String respDesc, String status,
                                 LocalDateTime receivedAt, LocalDateTime processedAt) {

        String reqId = generateReqId(receivedAt);
        String logId = reqId + "." + UUID.randomUUID().toString().substring(0, 8);
        String isoTimestamp = processedAt.atOffset(ZoneOffset.UTC).format(ISO_FMT);

        TransactionLog txLog = TransactionLog.builder()
                .timestamp(List.of(isoTimestamp))
                .appName(resolveProviderCode(apiName))
                .bankId("")
                .bankShortName("")
                .inXmlMsg(toJsonString(requestBody))
                .logId(logId)
                .msgOriginal("")
                .outXmlMsg(toJsonString(responseBody))
                .processDate(processedAt.format(DATE_FMT))
                .processTime(processedAt.format(TIME_FMT))
                .providerCode(resolveProviderCode(apiName))
                .receiverDate(receivedAt.format(DATE_FMT))
                .receiverId(String.valueOf(SEQ.getAndIncrement()))
                .receiverTime(receivedAt.format(TIME_FMT))
                .reqApp(appName)
                .reqDate(receivedAt.format(DATE_FMT))
                .reqId(reqId)
                .respCode(respCode)
                .respDate(processedAt.format(DATE_FMT))
                .respDesc(respDesc)
                .respTime(processedAt.format(TIME_FMT))
                .apiName(apiName)
                .spfUrl(spfUrl)
                .status(status)
                .step(status)
                .timeStamp(List.of(isoTimestamp))
                .index(logIndex)
                .score(null)
                .build();

        return toJsonString(txLog);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateReqId(LocalDateTime time) {
        return appName.toUpperCase() + time.format(REQ_ID_FMT) + "-" + (SEQ.get() % 1000);
    }

    private String resolveProviderCode(String apiName) {
        if (defaultProviderCode != null && !defaultProviderCode.isBlank()) {
            return defaultProviderCode;
        }
        return "ESB." + appName.toUpperCase() + "." + apiName;
    }

    private String toJsonString(Object obj) {
        if (obj == null) return "";
        if (obj instanceof String str) return str;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
