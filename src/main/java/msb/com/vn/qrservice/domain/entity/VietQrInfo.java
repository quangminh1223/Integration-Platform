package msb.com.vn.qrservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "VIET_QR_INFO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VietQrInfo {

    @Id
    @UuidGenerator
    @Column(name = "ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "QR_CODE_ID", length = 36, nullable = false)
    private String qrCodeId;

    @Column(name = "BANK_BIN", length = 10, nullable = false)
    private String bankBin;

    @Column(name = "BANK_ACCOUNT", length = 50, nullable = false)
    private String bankAccount;

    @Column(name = "ACCOUNT_NAME", length = 200)
    private String accountName;

    @Column(name = "AMOUNT", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "TRANSACTION_REF", length = 100)
    private String transactionRef;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
