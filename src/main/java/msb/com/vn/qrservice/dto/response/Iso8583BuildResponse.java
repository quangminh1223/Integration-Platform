package msb.com.vn.qrservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Kết quả build bản tin ISO8583")
public class Iso8583BuildResponse {

    @Schema(description = "Bản tin ISO8583 dạng hex string")
    private String hexMessage;

    @Schema(description = "MTI của bản tin", example = "0200")
    private String mti;

    @Schema(description = "Độ dài bản tin (bytes)", example = "128")
    private int messageLength;

    @Schema(description = "Thời điểm xử lý")
    private LocalDateTime processedAt;
}
