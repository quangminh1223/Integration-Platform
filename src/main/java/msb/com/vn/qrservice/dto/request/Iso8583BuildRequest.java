package msb.com.vn.qrservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Request build bản tin ISO8583 từ các field")
public class Iso8583BuildRequest {

    @NotBlank(message = "MTI không được để trống")
    @Schema(description = "Message Type Indicator (4 chữ số)", example = "0200")
    private String mti;

    @NotEmpty(message = "Danh sách field không được để trống")
    @Schema(
        description = "Map các field ISO8583: key = số field (1-128), value = giá trị",
        example = "{\"2\": \"4111111111111111\", \"3\": \"000000\", \"4\": \"000000010000\"}"
    )
    private Map<Integer, String> fields;

    @Schema(description = "Người thực hiện request", example = "user01")
    private String requestedBy;
}
