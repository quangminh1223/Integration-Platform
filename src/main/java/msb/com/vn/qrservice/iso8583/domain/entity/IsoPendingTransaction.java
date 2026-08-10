package msb.com.vn.qrservice.iso8583.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import msb.com.vn.qrservice.common.enums.IsoPendingStatus;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Giao dịch ISO8583 đã trả RC 68 (timeout) cho bên gọi nhưng chưa biết CORE có hạch toán hay không.
 *
 * <p>Bảng này là nguồn dữ liệu đối soát. CORE không hỗ trợ bản tin đảo 0400 nên không thể
 * tự động hoàn tác — mọi giao dịch timeout phải được lưu lại và theo dõi đến khi xác định
 * được kết quả thật, tránh lệch quỹ âm thầm.</p>
 *
 * <p>Ghi bằng đường async qua queue để không chặn thread xử lý giao dịch.</p>
 */
@Entity
@Table(
        name = "ISO_PENDING_TRANSACTION",
        indexes = {
                @Index(name = "IDX_ISO_PENDING_STATUS", columnList = "STATUS"),
                @Index(name = "IDX_ISO_PENDING_STAN", columnList = "STAN,TERMINAL_ID,LOCAL_TXN_DATE"),
                @Index(name = "IDX_ISO_PENDING_CREATED", columnList = "CREATED_AT")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IsoPendingTransaction {

    @Id
    @UuidGenerator
    @Column(name = "ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "CORRELATION_ID", length = 64, nullable = false)
    private String correlationId;

    // ─── Định danh giao dịch theo chuẩn ISO8583 ─────────────────────────────

    @Column(name = "MTI", length = 4, nullable = false)
    private String mti;

    /** Field 11 — System Trace Audit Number */
    @Column(name = "STAN", length = 12)
    private String stan;

    /** Field 41 — Card Acceptor Terminal ID */
    @Column(name = "TERMINAL_ID", length = 16)
    private String terminalId;

    /** Field 37 — Retrieval Reference Number */
    @Column(name = "RRN", length = 24)
    private String rrn;

    /** Field 13 — Local Transaction Date (MMdd), dùng cùng STAN để định danh trong ngày */
    @Column(name = "LOCAL_TXN_DATE", length = 8)
    private String localTxnDate;

    /** Field 2 — PAN đã che theo PCI DSS, không lưu số đầy đủ */
    @Column(name = "PAN_MASKED", length = 24)
    private String panMasked;

    /** Field 4 — Transaction Amount */
    @Column(name = "AMOUNT", length = 20)
    private String amount;

    /** Field 49 — Transaction Currency Code */
    @Column(name = "CURRENCY_CODE", length = 3)
    private String currencyCode;

    /** Field 3 — Processing Code */
    @Column(name = "PROCESSING_CODE", length = 6)
    private String processingCode;

    @Column(name = "REMOTE_ADDRESS", length = 64)
    private String remoteAddress;

    // ─── Trạng thái đối soát ────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 24, nullable = false)
    private IsoPendingStatus status;

    /** Response code ta đã trả cho bên gọi, thường là 68 */
    @Column(name = "RESPONSE_SENT", length = 4)
    private String responseSent;

    /** Response code CORE trả về muộn; null nếu CORE chưa từng trả lời */
    @Column(name = "CORE_RESPONSE_CODE", length = 4)
    private String coreResponseCode;

    /** Thời điểm CORE trả về muộn */
    @Column(name = "CORE_RESPONDED_AT")
    private LocalDateTime coreRespondedAt;

    /** Deadline áp dụng cho giao dịch (ms) */
    @Column(name = "DEADLINE_MS")
    private Long deadlineMs;

    /** Thời gian CORE thực tế xử lý đến khi trả về (ms); null nếu chưa trả */
    @Column(name = "CORE_ELAPSED_MS")
    private Long coreElapsedMs;

    @Column(name = "RECONCILE_ATTEMPTS")
    private Integer reconcileAttempts;

    @Lob
    @Column(name = "RECONCILE_NOTE")
    private String reconcileNote;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (reconcileAttempts == null) {
            reconcileAttempts = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
