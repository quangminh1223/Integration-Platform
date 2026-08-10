package msb.com.vn.qrservice.iso8583.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.util.IsoFieldDictionary;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import msb.com.vn.qrservice.queue.DeadLetterHandler;
import msb.com.vn.qrservice.queue.ManagedQueue;
import msb.com.vn.qrservice.queue.QueueManager;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ghi audit log giao dịch ISO8583 qua {@link ManagedQueue} — bounded, có TTL và DLQ.
 *
 * <h3>Vị trí trong pipeline</h3>
 * <pre>
 * nhận bản tin → unpack → [ AUDIT: offer vào queue ] → convert ISO→JSON → business flow → response
 *                                     │                                                      │
 *                          non-blocking, &lt; 1ms                          [ AUDIT: COMPLETED ]─┘
 * </pre>
 *
 * <p><b>Không nằm trên đường trả response</b>: {@code offer()} là non-blocking, queue đầy thì
 * bản ghi audit bị drop và có metric cảnh báo, nhưng giao dịch vẫn được xử lý và trả response.
 * Đây là lựa chọn có ý thức — mất một dòng log tốt hơn là mất một giao dịch.</p>
 *
 * <h3>Che dữ liệu nhạy cảm</h3>
 * <p>Các field trong {@code mask-fields} (mặc định 2, 35, 36, 45, 52, 55) được che trước khi
 * ghi log để không lưu PAN đầy đủ, track data và PIN block.</p>
 */
@Slf4j
@Service
public class Iso8583AuditService {

    public static final String QUEUE_NAME = "iso8583-audit";

    /** Logger riêng, route ra file qua logback-spring.xml */
    private static final org.slf4j.Logger AUDIT_LOG =
            org.slf4j.LoggerFactory.getLogger("ISO8583_AUDIT_LOG");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static final String DIRECTION_INBOUND = "INBOUND";
    private static final String DIRECTION_OUTBOUND = "OUTBOUND";

    private final QueueManager queueManager;
    private final DeadLetterHandler deadLetterHandler;
    private final Iso8583SocketProperties properties;

    private ManagedQueue<IsoAuditEntry> auditQueue;
    private Set<Integer> maskedFields;

    public Iso8583AuditService(QueueManager queueManager,
                               DeadLetterHandler deadLetterHandler,
                               Iso8583SocketProperties properties) {
        this.queueManager = queueManager;
        this.deadLetterHandler = deadLetterHandler;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Iso8583SocketProperties.Audit cfg = properties.getAudit();

        if (!cfg.isEnabled()) {
            log.info("ISO8583 audit đang TẮT (iso8583.socket.audit.enabled=false)");
            return;
        }

        this.maskedFields = cfg.getMaskFields().stream().collect(Collectors.toUnmodifiableSet());

        this.auditQueue = queueManager.register(
                QUEUE_NAME,
                cfg.getQueueCapacity(),
                cfg.getWorkerThreads(),
                cfg.getBatchSize(),
                cfg.getTtlMillis(),
                this::writeEntry,
                deadLetterHandler::handle);

        log.info("ISO8583 audit queue [{}] đã đăng ký: capacity={}, workers={}, batch={}, ttl={}ms, maskFields={}",
                QUEUE_NAME, cfg.getQueueCapacity(), cfg.getWorkerThreads(),
                cfg.getBatchSize(), cfg.getTtlMillis(), cfg.getMaskFields());
    }

    // ─── API (non-blocking) ─────────────────────────────────────────────────

    /**
     * Ghi audit ngay khi nhận được bản tin, TRƯỚC bước convert sang JSON.
     * Đảm bảo có dấu vết giao dịch kể cả khi các bước sau lỗi hoặc process chết.
     */
    public void logReceived(Iso8583Exchange exchange) {
        if (auditQueue == null) {
            return;
        }
        offer(baseBuilder(exchange, IsoAuditEntry.Phase.RECEIVED, DIRECTION_INBOUND)
                .receivedAt(format(exchange.getReceivedAt()))
                .build());
    }

    /**
     * Ghi audit khi đã dựng được response.
     */
    public void logCompleted(Iso8583Exchange exchange) {
        if (auditQueue == null) {
            return;
        }
        offer(baseBuilder(exchange, IsoAuditEntry.Phase.COMPLETED, DIRECTION_INBOUND)
                .responseCode(exchange.getResponseCode())
                .receivedAt(format(exchange.getReceivedAt()))
                .completedAt(format(exchange.getCompletedAt()))
                .durationMs(exchange.elapsedMs())
                .build());
    }

    /**
     * Ghi audit khi xử lý thất bại, vượt deadline hoặc bị từ chối do quá tải.
     */
    public void logFailure(Iso8583Exchange exchange, IsoAuditEntry.Phase phase, String errorMessage) {
        if (auditQueue == null) {
            return;
        }
        offer(baseBuilder(exchange, phase, DIRECTION_INBOUND)
                .responseCode(exchange.getResponseCode())
                .receivedAt(format(exchange.getReceivedAt()))
                .completedAt(format(Instant.now()))
                .durationMs(exchange.elapsedMs())
                .errorMessage(errorMessage)
                .build());
    }

    /**
     * Ghi audit cho bản tin gửi ra ngoài qua kênh outbound.
     */
    public void logOutbound(ISOMsg request, ISOMsg response, String correlationId,
                            String target, long durationMs, String errorMessage) {
        if (auditQueue == null) {
            return;
        }
        String mti = IsoMessageUtils.safeGetMti(request);
        boolean failed = errorMessage != null;

        offer(IsoAuditEntry.builder()
                .phase(failed ? IsoAuditEntry.Phase.FAILED : IsoAuditEntry.Phase.COMPLETED)
                .direction(DIRECTION_OUTBOUND)
                .correlationId(correlationId)
                .remoteAddress(target)
                .mti(mti)
                .mtiDescription(IsoFieldDictionary.mtiDescription(mti))
                .stan(IsoMessageUtils.getStan(request))
                .rrn(fieldOrNull(request, IsoMessageUtils.FIELD_RRN))
                .processingCode(fieldOrNull(request, 3))
                .amount(fieldOrNull(request, 4))
                .terminalId(fieldOrNull(request, 41))
                .responseCode(response != null
                        ? fieldOrNull(response, IsoMessageUtils.FIELD_RESPONSE_CODE) : null)
                .fields(maskFields(IsoMessageUtils.toFieldMap(request)))
                .completedAt(format(Instant.now()))
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .build());
    }

    /**
     * Metrics của queue audit.
     */
    public ManagedQueue.QueueStats getQueueStats() {
        return auditQueue != null ? auditQueue.getStats() : null;
    }

    // ─── Nội bộ ─────────────────────────────────────────────────────────────

    private IsoAuditEntry.IsoAuditEntryBuilder baseBuilder(Iso8583Exchange exchange,
                                                           IsoAuditEntry.Phase phase,
                                                           String direction) {
        ISOMsg request = exchange.getIsoRequest();
        return IsoAuditEntry.builder()
                .phase(phase)
                .direction(direction)
                .correlationId(exchange.getCorrelationId())
                .remoteAddress(exchange.getRemoteAddress())
                .mti(exchange.getMti())
                .mtiDescription(IsoFieldDictionary.mtiDescription(exchange.getMti()))
                .stan(exchange.getStan())
                .rrn(fieldOrNull(request, IsoMessageUtils.FIELD_RRN))
                .processingCode(fieldOrNull(request, 3))
                .amount(fieldOrNull(request, 4))
                .terminalId(fieldOrNull(request, 41))
                .fields(maskFields(IsoMessageUtils.toFieldMap(request)))
                .rawHex(properties.getAudit().isLogRawHex()
                        ? IsoMessageUtils.bytesToHex(exchange.getRawRequest()) : null)
                .messageLength(exchange.getRawRequest() != null ? exchange.getRawRequest().length : 0);
    }

    /**
     * Đưa bản ghi vào queue. Non-blocking — queue đầy thì drop, không chặn giao dịch.
     */
    private void offer(IsoAuditEntry entry) {
        if (!auditQueue.offer(entry)) {
            log.warn("Audit queue đầy — bỏ bản ghi phase={} correlationId={}",
                    entry.phase(), entry.correlationId());
        }
    }

    /**
     * Consumer của queue — chạy trên writer thread, không phải business thread.
     */
    private void writeEntry(IsoAuditEntry entry) {
        try {
            AUDIT_LOG.info(MAPPER.writeValueAsString(entry));
        } catch (JsonProcessingException e) {
            AUDIT_LOG.warn("Không serialize được audit entry correlationId={}: {}",
                    entry.correlationId(), e.getMessage());
        }
    }

    /**
     * Che dữ liệu nhạy cảm: PAN giữ 6 số đầu + 4 số cuối, còn lại che toàn bộ.
     */
    private Map<String, String> maskFields(Map<Integer, String> fields) {
        Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            int fieldNumber = entry.getKey();
            String value = entry.getValue();

            if (maskedFields != null && maskedFields.contains(fieldNumber)) {
                value = fieldNumber == 2 ? maskPan(value) : maskAll(value);
            }
            masked.put(String.valueOf(fieldNumber), value);
        }
        return masked;
    }

    /** PAN: giữ 6 số đầu và 4 số cuối theo thông lệ PCI DSS */
    private String maskPan(String pan) {
        if (pan == null || pan.length() <= 10) {
            return maskAll(pan);
        }
        return pan.substring(0, 6)
                + "*".repeat(pan.length() - 10)
                + pan.substring(pan.length() - 4);
    }

    private String maskAll(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return "*".repeat(Math.min(value.length(), 32));
    }

    private String fieldOrNull(ISOMsg msg, int field) {
        if (msg == null || !msg.hasField(field)) {
            return null;
        }
        return msg.getString(field);
    }

    private String format(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TS_FMT);
    }
}
