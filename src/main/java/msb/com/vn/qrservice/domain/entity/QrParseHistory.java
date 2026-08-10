package msb.com.vn.qrservice.domain.entity;

import msb.com.vn.qrservice.common.enums.QrStatus;
import msb.com.vn.qrservice.common.enums.QrType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "QR_PARSE_HISTORY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrParseHistory {

    @Id
    @UuidGenerator
    @Column(name = "ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Lob
    @Column(name = "RAW_CONTENT", nullable = false)
    private String rawContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "QR_TYPE", length = 50)
    private QrType qrType;

    @Lob
    @Column(name = "PARSED_DATA")
    private String parsedData;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private QrStatus status;

    @Column(name = "ERROR_MESSAGE", length = 500)
    private String errorMessage;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
