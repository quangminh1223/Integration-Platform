package msb.com.vn.qrservice.kafka.event;

import msb.com.vn.qrservice.common.enums.QrType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrGeneratedEvent {

    private String qrCodeId;
    private QrType qrType;
    private String createdBy;
    private LocalDateTime createdAt;
}
