package msb.com.vn.qrservice.dto.request;

import msb.com.vn.qrservice.common.enums.QrType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateQrRequest {

    @NotNull(message = "Loại QR không được để trống")
    private QrType qrType;

    @NotBlank(message = "Nội dung QR không được để trống")
    private String content;

    private Integer width;
    private Integer height;
    private String createdBy;
}
