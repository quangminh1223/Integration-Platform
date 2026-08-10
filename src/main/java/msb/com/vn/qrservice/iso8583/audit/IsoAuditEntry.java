package msb.com.vn.qrservice.iso8583.audit;

import lombok.Builder;

import java.util.Map;

/**
 * Một bản ghi audit cho bản tin ISO8583, đưa vào queue để ghi log async.
 *
 * <p>Immutable — an toàn khi truyền giữa worker thread và writer thread của queue.</p>
 */
@Builder
public record IsoAuditEntry(

        /** Giai đoạn trong pipeline */
        Phase phase,

        /** ID truy vết */
        String correlationId,

        /** Địa chỉ hệ thống gửi bản tin */
        String remoteAddress,

        /** Hướng bản tin */
        String direction,

        String mti,
        String mtiDescription,
        String stan,
        String rrn,
        String processingCode,
        String amount,
        String terminalId,
        String responseCode,

        /** Các field đã che dữ liệu nhạy cảm */
        Map<String, String> fields,

        /** Bản tin gốc dạng hex — chỉ có khi bật log-raw-hex */
        String rawHex,

        /** Số byte của bản tin */
        int messageLength,

        String receivedAt,
        String completedAt,
        Long durationMs,
        String errorMessage
) {

    public enum Phase {
        /** Vừa nhận và unpack xong, trước khi convert sang JSON */
        RECEIVED,
        /** Đã xử lý xong và dựng được response */
        COMPLETED,
        /** Xử lý thất bại */
        FAILED,
        /** Bị từ chối do quá tải */
        REJECTED,
        /** Vượt deadline xử lý */
        TIMED_OUT
    }
}
