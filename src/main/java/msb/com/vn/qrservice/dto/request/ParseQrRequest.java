package msb.com.vn.qrservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ParseQrRequest {

    @NotBlank(message = "Nội dung QR không được để trống")
    private String rawContent;

    private String createdBy;
}
