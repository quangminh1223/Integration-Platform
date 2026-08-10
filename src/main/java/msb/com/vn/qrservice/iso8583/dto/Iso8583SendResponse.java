package msb.com.vn.qrservice.iso8583.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kết quả gửi bản tin ISO8583 qua socket outbound.
 */
@Data
@Builder
@Schema(description = "Kết quả gửi bản tin ISO8583 qua socket outbound")
public class Iso8583SendResponse {

    @Schema(description = "ID truy vết")
    private String correlationId;

    @Schema(description = "Địa chỉ đích đã gửi", example = "localhost:1112")
    private String target;

    @Schema(description = "MTI của bản tin gửi", example = "0200")
    private String requestMti;

    @Schema(description = "STAN dùng để correlate", example = "000123")
    private String stan;

    @Schema(description = "Bản tin gửi dạng hex")
    private String requestHex;

    @Schema(description = "MTI của bản tin nhận về", example = "0210")
    private String responseMti;

    @Schema(description = "Response code (field 39)", example = "00")
    private String responseCode;

    @Schema(description = "Các field trong bản tin response")
    private Map<Integer, String> responseFields;

    @Schema(description = "Bản tin response dạng hex")
    private String responseHex;

    @Schema(description = "Thời gian round-trip (ms)")
    private long roundTripMs;

    @Schema(description = "Thời điểm hoàn tất")
    private LocalDateTime completedAt;
}
