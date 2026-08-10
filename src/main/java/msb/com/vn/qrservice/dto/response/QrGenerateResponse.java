package msb.com.vn.qrservice.dto.response;

import msb.com.vn.qrservice.common.enums.QrType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QrGenerateResponse {

    private String id;
    private QrType qrType;
    private String content;
    private String imageBase64;
    private LocalDateTime createdAt;
}
