package msb.com.vn.qrservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Schema(description = "Kết quả chuyển đổi thông tin giao dịch sang bản tin ISO8583")
public class TransferIso8583Response {

    // ── Bản tin ISO8583 ──────────────────────────────────────────────────────

    @Schema(description = "Bản tin ISO8583 đầy đủ dạng hex string (MTI + Bitmap + Data)")
    private String hexMessage;

    @Schema(description = "Bản tin ISO8583 kèm 2-byte length prefix (dùng cho TCP/NACChannel)")
    private String hexMessageWithLengthPrefix;

    @Schema(description = "Độ dài bản tin (bytes)", example = "128")
    private int messageLength;

    // ── Thông tin MTI & Bitmap ───────────────────────────────────────────────

    @Schema(description = "Message Type Indicator", example = "0200")
    private String mti;

    @Schema(description = "Mô tả loại bản tin", example = "Financial Transaction Request")
    private String mtiDescription;

    @Schema(description = "Primary Bitmap (hex, DE 1-64)", example = "F220000002C00000")
    private String primaryBitmap;

    @Schema(description = "Secondary Bitmap (hex, DE 65-128)", example = "0000000000C00000")
    private String secondaryBitmap;

    // ── Các field ISO8583 đã map ─────────────────────────────────────────────

    @Schema(description = "Chi tiết từng field ISO8583 đã được set")
    private Map<String, FieldDetail> fieldMapping;

    // ── Thông tin bổ sung ────────────────────────────────────────────────────

    @Schema(description = "STAN (System Trace Audit Number) tự sinh", example = "000001")
    private String stan;

    @Schema(description = "RRN (Retrieval Reference Number) tự sinh", example = "052514000001")
    private String rrn;

    @Schema(description = "Thời điểm xử lý")
    private LocalDateTime processedAt;

    // ── Inner class ──────────────────────────────────────────────────────────

    @Data
    @Builder
    @Schema(description = "Chi tiết một field ISO8583")
    public static class FieldDetail {

        @Schema(description = "Số DE", example = "2")
        private int de;

        @Schema(description = "Tên field", example = "Primary Account Number (PAN)")
        private String name;

        @Schema(description = "Tên field API tương ứng", example = "creditAccount")
        private String apiField;

        @Schema(description = "Giá trị gốc từ API", example = "80000002233")
        private String inputValue;

        @Schema(description = "Giá trị đã pack vào ISO8583", example = "1180000002233")
        private String packedValue;

        @Schema(description = "Kiểu dữ liệu ISO8583", example = "LLVAR-N")
        private String dataType;
    }
}