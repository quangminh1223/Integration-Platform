package msb.com.vn.qrservice.iso8583.pending;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.enums.IsoPendingStatus;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.domain.entity.IsoPendingTransaction;
import msb.com.vn.qrservice.iso8583.domain.repository.IsoPendingTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Job đối soát giao dịch treo.
 *
 * <p>CORE không hỗ trợ bản tin đảo 0400 nên không thể tự động hoàn tác. Job này làm hai việc:</p>
 *
 * <ol>
 *   <li><b>Leo thang</b>: giao dịch ở {@code TIMEOUT_UNKNOWN} quá lâu mà CORE không hề trả lời
 *       → chuyển {@code NEEDS_MANUAL} và log ở mức ERROR để alert bắt được. Đây là các giao dịch
 *       bắt buộc phải có người tra soát với CORE.</li>
 *   <li><b>Phơi bày lệch quỹ</b>: đếm số giao dịch {@code CORE_CONFIRMED} — những giao dịch mà
 *       CORE đã hạch toán nhưng bên gọi nhận RC 68. Mỗi bản ghi là một khoản lệch thật.</li>
 * </ol>
 *
 * <p>Job KHÔNG tự sửa dữ liệu tài chính. Nó chỉ phát hiện và báo cáo — việc điều chỉnh
 * phải do nghiệp vụ quyết định.</p>
 */
@Slf4j
@Component
public class PendingReconciliationJob {

    private final IsoPendingTransactionRepository repository;
    private final PendingTransactionWriter writer;
    private final Iso8583SocketProperties properties;
    private final MeterRegistry meterRegistry;

    /** Gauge theo từng trạng thái, cập nhật mỗi lượt job */
    private final Map<IsoPendingStatus, AtomicLong> statusGauges = new EnumMap<>(IsoPendingStatus.class);

    public PendingReconciliationJob(IsoPendingTransactionRepository repository,
                                    PendingTransactionWriter writer,
                                    Iso8583SocketProperties properties,
                                    MeterRegistry meterRegistry) {
        this.repository = repository;
        this.writer = writer;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void initMetrics() {
        for (IsoPendingStatus status : IsoPendingStatus.values()) {
            AtomicLong gauge = new AtomicLong(0);
            statusGauges.put(status, gauge);
            meterRegistry.gauge("iso8583.pending.transactions",
                    io.micrometer.core.instrument.Tags.of("status", status.name()), gauge);
        }
    }

    /**
     * Chạy theo chu kỳ cấu hình tại {@code iso8583.socket.pending.reconcile-interval-ms}.
     */
    @Scheduled(fixedDelayString = "${iso8583.socket.pending.reconcile-interval-ms:300000}")
    public void reconcile() {
        if (!properties.getPending().isEnabled()) {
            return;
        }

        try {
            // Gọi qua bean khác để @Transactional có hiệu lực (tránh self-invocation)
            Iso8583SocketProperties.Pending cfg = properties.getPending();
            writer.escalateStaleTransactions(
                    cfg.getManualEscalationMinutes(), cfg.getReconcileBatchSize());
            refreshMetrics();
            reportFundDiscrepancy();
        } catch (Exception e) {
            log.error("Job đối soát giao dịch treo lỗi: {}", e.getMessage(), e);
        }
    }

    /**
     * Cập nhật gauge số lượng theo từng trạng thái.
     */
    protected void refreshMetrics() {
        Map<IsoPendingStatus, Long> counts = new EnumMap<>(IsoPendingStatus.class);
        for (Object[] row : repository.countGroupedByStatus()) {
            counts.put((IsoPendingStatus) row[0], (Long) row[1]);
        }
        statusGauges.forEach((status, gauge) -> gauge.set(counts.getOrDefault(status, 0L)));
    }

    /**
     * Báo cáo lệch quỹ: CORE đã hạch toán nhưng bên gọi nhận RC 68.
     */
    protected void reportFundDiscrepancy() {
        long confirmed = repository.countByStatus(IsoPendingStatus.CORE_CONFIRMED);
        long needsManual = repository.countByStatus(IsoPendingStatus.NEEDS_MANUAL);

        if (confirmed > 0) {
            log.error("LỆCH QUỸ ĐANG TỒN: {} giao dịch CORE đã hạch toán nhưng bên gọi nhận RC 68. "
                    + "Tra danh sách tại GET /api/v1/iso8583/pending?status=CORE_CONFIRMED", confirmed);
        }
        if (needsManual > 0) {
            log.error("CHỜ TRA SOÁT: {} giao dịch cần người xử lý với CORE. "
                    + "Tra danh sách tại GET /api/v1/iso8583/pending?status=NEEDS_MANUAL", needsManual);
        }
    }
}
