package msb.com.vn.qrservice.common.enums;

/**
 * Trạng thái giao dịch ISO8583 bị timeout, chờ đối soát với CORE.
 *
 * <p>Dùng khi CORE không hỗ trợ bản tin đảo (0400 Reversal): ta đã trả RC 68 cho bên gọi
 * nhưng không biết CORE có hạch toán hay không, nên phải theo dõi đến khi xác định được.</p>
 *
 * <pre>
 * TIMEOUT_UNKNOWN ──CORE trả về muộn, RC thành công──► CORE_CONFIRMED ──► RECONCILED
 *        │                                                (LỆCH QUỸ)
 *        ├─────────CORE trả về muộn, RC lỗi────────────► CORE_DECLINED  ──► RECONCILED
 *        │                                              (an toàn)
 *        └─────────quá hạn theo dõi, không có tin───────► NEEDS_MANUAL
 * </pre>
 */
public enum IsoPendingStatus {

    /**
     * Đã trả RC 68 cho bên gọi, chưa biết CORE hạch toán hay chưa.
     * Trạng thái khởi tạo của mọi giao dịch timeout.
     */
    TIMEOUT_UNKNOWN,

    /**
     * CORE trả về muộn với response code thành công — tiền ĐÃ bị trừ thật,
     * trong khi bên gọi tưởng giao dịch thất bại. Đây là lệch quỹ, cần xử lý ngay.
     */
    CORE_CONFIRMED,

    /**
     * CORE trả về muộn với response code lỗi — CORE không hạch toán.
     * Bên gọi nhận RC 68 là khớp thực tế, an toàn, có thể đóng.
     */
    CORE_DECLINED,

    /** Đã đối soát và xử lý xong */
    RECONCILED,

    /**
     * Quá thời gian theo dõi mà không có thông tin từ CORE — cần người vào xử lý thủ công.
     */
    NEEDS_MANUAL
}
