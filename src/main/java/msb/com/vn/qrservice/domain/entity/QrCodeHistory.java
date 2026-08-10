package msb.com.vn.qrservice.domain.entity;

import msb.com.vn.qrservice.common.enums.QrStatus;
import msb.com.vn.qrservice.common.enums.QrType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "QR_CODE_HISTORY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrCodeHistory {

    @Id
    @UuidGenerator
    @Column(name = "ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "QR_TYPE", length = 50, nullable = false)
    private QrType qrType;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    @Lob
    @Column(name = "IMAGE_BASE64")
    private String imageBase64;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private QrStatus status;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
