package msb.com.vn.qrservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ParseQrImageRequest {

    @NotBlank(message = "Ảnh QR (base64) không được để trống")
    private String imageBase64;

    private String createdBy;
}
