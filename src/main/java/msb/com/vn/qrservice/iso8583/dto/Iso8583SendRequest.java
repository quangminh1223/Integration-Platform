package msb.com.vn.qrservice.iso8583.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

/**
 * Yêu cầu gửi bản tin ISO8583 ra ngoài qua socket outbound.
 */
@Data
@Schema(description = "Yêu cầu gửi bản tin ISO8583 qua socket outbound")
public class Iso8583SendRequest {

    @NotBlank(message = "MTI không được để trống")
    @Pattern(regexp = "\\d{4}", message = "MTI phải gồm đúng 4 chữ số")
    @Schema(description = "Message Type Indicator", example = "0200")
    private String mti;

    @NotEmpty(message = "Danh sách field không được để trống")
    @Schema(description = "Map số field → giá trị. Field 11 (STAN) sẽ tự sinh nếu không truyền.",
            example = "{\"3\":\"400000\",\"4\":\"000000500000\",\"41\":\"TERM0001\",\"49\":\"704\"}")
    private Map<Integer, String> fields;

    @Schema(description = "ID truy vết. Bỏ trống để hệ thống tự sinh.")
    private String correlationId;

    @Schema(description = "true = chỉ gửi, không chờ response", defaultValue = "false")
    private boolean fireAndForget = false;
}
