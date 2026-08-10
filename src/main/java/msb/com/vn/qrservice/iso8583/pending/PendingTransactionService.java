package msb.com.vn.qrservice.iso8583.pending;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.enums.IsoPendingStatus;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.domain.entity.IsoPendingTransaction;
import msb.com.vn.qrservice.iso8583.domain.repository.IsoPendingTransactionRepository;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import msb.com.vn.qrservice.queue.DeadLetterHandler;
import msb.com.vn.qrservice.queue.ManagedQueue;
import msb.com.vn.qrservice.queue.QueueManager;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Quản lý giao dịch ISO8583 bị timeout, chờ đối soát với CORE.
 *
 * <p><b>Bối cảnh:</b> CORE không hỗ trợ bản tin đảo 0400, nên khi ta trả RC 68 do timeout,
 * không có cách nào tự động hoàn tác. Mọi giao dịch timeout phải được ghi lại để đối soát,
 * nếu không sẽ lệch quỹ âm thầm.</p>
 *
 * <h3>Hai thời điểm ghi</h3>
 * <ol>
 *   <li><b>Khi timeout</b> — ghi bản ghi trạng thái {@code TIMEOUT_UNKNOWN}</li>
 *   <li><b>Khi CORE trả về muộn</b> — cập nhật thành {@code CORE_CONFIRMED} (tiền đã trừ,
 *       lệch quỹ) hoặc {@code CORE_DECLINED} (không trừ, an toàn)</li>
 * </ol>
 *
 * <p>Thời điểm thứ hai là thông tin quý nhất: nó cho biết chính xác CORE đã làm gì,
 * không cần chờ đối soát cuối ngày.</p>
 *
 * <p>Ghi qua {@link ManagedQueue} để không chặn thread xử lý giao dịch. Queue có DLQ nên
 * bản ghi không bị mất im lặng nếu DB sự cố — nó sẽ nằm trong {@code dead-letter.log}.</p>
 */
@Slf4j
@Service
public class PendingTransactionService {

    public static final String QUEUE_NAME = "iso8583-pending-write";

    private final IsoPendingTransactionRepository repository;
    private final PendingTransactionWriter writer;
    private final QueueManager queueManager;
    private final DeadLetterHandler deadLetterHandler;
    private final Iso8583SocketProperties properties;

    private ManagedQueue<PendingWrite> writeQueue;

    public PendingTransactionService(IsoPendingTransactionRepository repository,
                                     PendingTransactionWriter writer,
                                     QueueManager queueManager,
                                     DeadLetterHandler deadLetterHandler,
                                     Iso8583SocketProperties properties) {
        this.repository = repository;
        this.writer = writer;
        this.queueManager = queueManager;
        this.deadLetterHandler = deadLetterHandler;
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        Iso8583SocketProperties.Pending cfg = properties.getPending();
        if (!cfg.isEnabled()) {
            log.warn("Theo dõi giao dịch treo đang TẮT. CORE không hỗ trợ reversal nên "
                    + "giao dịch timeout sẽ KHÔNG được đối soát — chỉ tắt khi đã có cơ chế khác.");
            return;
        }

        writeQueue = queueManager.register(
                QUEUE_NAME,
                cfg.getQueueCapacity(),
                cfg.getWriterThreads(),
                cfg.getBatchSize(),
                cfg.getTtlMillis(),
                this::persist,
                deadLetterHandler::handle);

        log.info("Theo dõi giao dịch treo đã bật: queue [{}] capacity={}, workers={}",
                QUEUE_NAME, cfg.getQueueCapacity(), cfg.getWriterThreads());
    }

    // ─── API (non-blocking) ─────────────────────────────────────────────────

    /**
     * Ghi nhận giao dịch vừa bị timeout — đã trả RC 68, chưa biết CORE làm gì.
     */
    public void recordTimeout(Iso8583Exchange exchange, String responseSent, long deadlineMs) {
        if (writeQueue == null) {
            return;
        }
        log.error("GIAO DỊCH TREO — đã trả RC {} nhưng CORE chưa trả lời. "
                        + "stan={}, terminal={}, amount={}, correlationId={}. "
                        + "Bản ghi được đưa vào bảng đối soát.",
                responseSent, exchange.getStan(),
                fieldOrNull(exchange.getIsoRequest(), 41),
                fieldOrNull(exchange.getIsoRequest(), 4),
                exchange.getCorrelationId());

        offer(PendingWrite.timeout(exchange, responseSent, deadlineMs));
    }

    /**
     * Ghi nhận CORE trả về MUỘN, sau khi ta đã trả RC 68 cho bên gọi.
     *
     * <p>Đây là thông tin quyết định: response code của CORE cho biết tiền có bị trừ hay không.</p>
     */
    public void recordLateCoreResponse(Iso8583Exchange exchange, String coreResponseCode) {
        if (writeQueue == null) {
            return;
        }
        boolean corePosted = isApproved(coreResponseCode);

        if (corePosted) {
            log.error("LỆCH QUỸ — CORE hạch toán THÀNH CÔNG (RC {}) sau khi ta đã trả RC 68. "
                            + "Tiền đã bị trừ nhưng bên gọi tưởng thất bại. "
                            + "stan={}, amount={}, correlationId={}, CORE mất {}ms",
                    coreResponseCode, exchange.getStan(),
                    fieldOrNull(exchange.getIsoRequest(), 4),
                    exchange.getCorrelationId(), exchange.elapsedMs());
        } else {
            log.warn("CORE từ chối (RC {}) sau deadline — khớp với RC 68 đã trả, không lệch quỹ. "
                            + "stan={}, correlationId={}",
                    coreResponseCode, exchange.getStan(), exchange.getCorrelationId());
        }

        offer(PendingWrite.lateResponse(exchange, coreResponseCode, corePosted));
    }

    /**
     * Cập nhật kết quả đối soát cho một bản ghi.
     */
    @Transactional
    public void markReconciled(String id, String note) {
        repository.findById(id).ifPresent(entity -> {
            entity.setStatus(IsoPendingStatus.RECONCILED);
            entity.setReconcileNote(note);
            entity.setReconcileAttempts(safeAttempts(entity) + 1);
            repository.save(entity);
            log.info("Đã đối soát xong giao dịch treo id={}, stan={}", id, entity.getStan());
        });
    }

    public ManagedQueue.QueueStats getQueueStats() {
        return writeQueue != null ? writeQueue.getStats() : null;
    }

    // ─── Consumer của queue (chạy trên writer thread) ────────────────────────

    /**
     * Uỷ quyền cho {@link PendingTransactionWriter} để {@code @Transactional} có hiệu lực
     * (gọi qua bean khác, không phải self-invocation).
     */
    private void persist(PendingWrite write) {
        writer.write(write, write.lateResponse && isApproved(write.coreResponseCode));
    }

    private void offer(PendingWrite write) {
        if (!writeQueue.offer(write)) {
            // Queue đầy là tình huống nghiêm trọng: mất dấu vết đối soát.
            // Log ở mức ERROR để alert bắt được, kèm đủ dữ liệu để dựng lại thủ công.
            log.error("QUEUE GIAO DỊCH TREO ĐẦY — không ghi được bản ghi đối soát! "
                            + "correlationId={}, stan={}, terminal={}, amount={}, coreRC={}",
                    write.correlationId, write.stan, write.terminalId,
                    write.amount, write.coreResponseCode);
        }
    }

    private boolean isApproved(String responseCode) {
        return properties.getPending().getApprovedResponseCodes().contains(responseCode);
    }

    private int safeAttempts(IsoPendingTransaction entity) {
        return entity.getReconcileAttempts() != null ? entity.getReconcileAttempts() : 0;
    }

    private static String fieldOrNull(ISOMsg msg, int field) {
        return msg != null && msg.hasField(field) ? msg.getString(field) : null;
    }

    /**
     * Bản ghi bất biến truyền qua queue — không giữ tham chiếu tới ISOMsg
     * để writer thread không đọc object đang bị thread khác thay đổi.
     */
    public static final class PendingWrite {
        final String correlationId;
        final String mti;
        final String stan;
        final String terminalId;
        final String rrn;
        final String localTxnDate;
        final String panMasked;
        final String amount;
        final String currencyCode;
        final String processingCode;
        final String remoteAddress;
        final String responseSent;
        final Long deadlineMs;
        final Long elapsedMs;

        final boolean lateResponse;
        final String coreResponseCode;
        final boolean corePosted;

        private PendingWrite(Iso8583Exchange exchange, String responseSent, Long deadlineMs,
                             boolean lateResponse, String coreResponseCode, boolean corePosted) {
            ISOMsg msg = exchange.getIsoRequest();
            this.correlationId = exchange.getCorrelationId();
            this.mti = exchange.getMti();
            this.stan = exchange.getStan();
            this.terminalId = fieldOrNull(msg, 41);
            this.rrn = fieldOrNull(msg, IsoMessageUtils.FIELD_RRN);
            this.localTxnDate = fieldOrNull(msg, IsoMessageUtils.FIELD_LOCAL_DATE);
            this.panMasked = maskPan(fieldOrNull(msg, 2));
            this.amount = fieldOrNull(msg, 4);
            this.currencyCode = fieldOrNull(msg, 49);
            this.processingCode = fieldOrNull(msg, 3);
            this.remoteAddress = exchange.getRemoteAddress();
            this.responseSent = responseSent;
            this.deadlineMs = deadlineMs;
            this.elapsedMs = exchange.elapsedMs();
            this.lateResponse = lateResponse;
            this.coreResponseCode = coreResponseCode;
            this.corePosted = corePosted;
        }

        static PendingWrite timeout(Iso8583Exchange exchange, String responseSent, long deadlineMs) {
            return new PendingWrite(exchange, responseSent, deadlineMs, false, null, false);
        }

        static PendingWrite lateResponse(Iso8583Exchange exchange, String coreResponseCode,
                                         boolean corePosted) {
            return new PendingWrite(exchange, null, null, true, coreResponseCode, corePosted);
        }

        /** PAN giữ 6 số đầu + 4 số cuối theo PCI DSS */
        private static String maskPan(String pan) {
            if (pan == null || pan.length() <= 10) {
                return pan == null ? null : "*".repeat(pan.length());
            }
            return pan.substring(0, 6)
                    + "*".repeat(pan.length() - 10)
                    + pan.substring(pan.length() - 4);
        }

    }
}
