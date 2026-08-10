package msb.com.vn.qrservice.dto.response;

import msb.com.vn.qrservice.common.enums.QrType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QrParseResponse {

    private String id;
    private String rawContent;
    private QrType qrType;
    private Object parsedData;
    private LocalDateTime createdAt;
}
