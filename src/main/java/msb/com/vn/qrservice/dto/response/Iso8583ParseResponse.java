package msb.com.vn.qrservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Schema(description = "Kết quả parse bản tin ISO8583")
public class Iso8583ParseResponse {

    @Schema(description = "Message Type Indicator", example = "0200")
    private String mti;

    @Schema(description = "Mô tả loại bản tin", example = "Financial Transaction Request")
    private String mtiDescription;

    @Schema(description = "Primary Bitmap (hex)", example = "4000000000000000")
    private String primaryBitmap;

    @Schema(description = "Secondary Bitmap (hex, nếu có)")
    private String secondaryBitmap;

    @Schema(description = "Danh sách các field đã parse: key = số field, value = thông tin field")
    private Map<Integer, FieldInfo> fields;

    @Schema(description = "Tổng số field có trong bản tin")
    private int totalFields;

    @Schema(description = "Thời điểm xử lý")
    private LocalDateTime processedAt;

    @Data
    @Builder
    @Schema(description = "Thông tin một field ISO8583")
    public static class FieldInfo {

        @Schema(description = "Số field", example = "2")
        private int fieldNumber;

        @Schema(description = "Tên field", example = "Primary Account Number (PAN)")
        private String fieldName;

        @Schema(description = "Giá trị field", example = "4111111111111111")
        private String value;

        @Schema(description = "Độ dài giá trị", example = "16")
        private int length;
    }
}
