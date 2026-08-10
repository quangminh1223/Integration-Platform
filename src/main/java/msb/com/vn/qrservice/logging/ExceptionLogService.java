package msb.com.vn.qrservice.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.BackendErrorType;
import msb.com.vn.qrservice.queue.ManagedQueue;
import msb.com.vn.qrservice.queue.QueueManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi log exception ASYNC ra file riêng — dùng QueueManager quản lý queue tập trung.
 *
 * Business thread chỉ serialize + offer vào ManagedQueue (< 1ms).
 */
@Slf4j
@Service
public class ExceptionLogService {

    public static final String QUEUE_NAME = "exception-log";

    private static final org.slf4j.Logger EX_LOG =
            org.slf4j.LoggerFactory.getLogger("EXCEPTION_LOG");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final QueueManager queueManager;
    private final DeadLetterHandler deadLetterHandler;
    private ManagedQueue<String> exQueue;

    @Value("${exception-log.queue-capacity:5000}")
    private int queueCapacity;

    @Value("${exception-log.writer-threads:1}")
    private int writerThreads;

    @Value("${exception-log.batch-size:50}")
    private int batchSize;

    @Value("${exception-log.ttl-millis:180000}")
    private long ttlMillis;

    @Value("${exception-log.include-stacktrace:true}")
    private boolean includeStackTrace;

    public ExceptionLogService(QueueManager queueManager, DeadLetterHandler deadLetterHandler) {
        this.queueManager = queueManager;
        this.deadLetterHandler = deadLetterHandler;
    }

    @PostConstruct
    public void init() {
        exQueue = queueManager.register(QUEUE_NAME, queueCapacity, writerThreads, batchSize,
                ttlMillis,
                EX_LOG::error,
                deadLetterHandler::handle);
        log.info("ExceptionLogService registered queue [{}] với TTL={}ms", QUEUE_NAME, ttlMillis);
    }

    // ─── Public API (non-blocking) ────────────────────────────────────────────

    public void logException(String operationId, String targetUrl,
                             BackendErrorType errorType, String message,
                             Throwable throwable) {
        String logJson = buildLogJson(operationId, targetUrl, errorType, message, throwable);
        exQueue.offer(logJson);
    }

    public String getMetrics() {
        ManagedQueue.QueueStats s = exQueue.getStats();
        return String.format("queue=%d/%d, processed=%d, dropped=%d",
                s.currentSize(), s.capacity(), s.processed(), s.dropped());
    }

    // ─── Build log JSON ─────────────────────────────────────────────────────

    private String buildLogJson(String operationId, String targetUrl,
                                BackendErrorType errorType, String message,
                                Throwable throwable) {
        Map<String, Object> logMap = new LinkedHashMap<>();
        logMap.put("@timestamp", LocalDateTime.now().format(TS_FMT));
        logMap.put("level", "ERROR");
        logMap.put("operationId", operationId);
        logMap.put("targetUrl", targetUrl);
        logMap.put("errorType", errorType != null ? errorType.getCode() : "UNKNOWN");
        logMap.put("retryable", errorType != null && errorType.isRetryable());
        logMap.put("message", message);
        logMap.put("thread", Thread.currentThread().getName());

        if (throwable != null) {
            logMap.put("exceptionClass", throwable.getClass().getName());
            logMap.put("exceptionMessage", throwable.getMessage());
            if (includeStackTrace) {
                logMap.put("stackTrace", getStackTrace(throwable));
            }
        }

        try {
            return MAPPER.writeValueAsString(logMap);
        } catch (Exception e) {
            return logMap.toString();
        }
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        return trace.length() > 4000 ? trace.substring(0, 4000) + "...[truncated]" : trace;
    }
}
