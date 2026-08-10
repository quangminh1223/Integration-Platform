package msb.com.vn.qrservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request parse bản tin ISO8583 từ hex string")
public class Iso8583ParseRequest {

    @NotBlank(message = "Bản tin ISO8583 (hex) không được để trống")
    @Schema(
        description = "Bản tin ISO8583 dạng hex string (bao gồm MTI + bitmap + data elements)",
        example = "02004000000000000000000000000000001234567890"
    )
    private String hexMessage;

    @Schema(description = "Người thực hiện request", example = "user01")
    private String requestedBy;
}
