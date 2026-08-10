package msb.com.vn.dsign.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for persistent audit storage of all D-Sign operations.
 * Mapped to the DSIGN_AUDIT_LOG table.
 */
@Entity
@Table(name = "DSIGN_AUDIT_LOG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigningAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dsign_audit_seq")
    @SequenceGenerator(name = "dsign_audit_seq", sequenceName = "DSIGN_AUDIT_LOG_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CORRELATION_ID", nullable = false)
    private String correlationId;

    @Column(name = "SIGNER_ID")
    private String signerId;

    @Column(name = "CERTIFICATE_REF")
    private String certificateRef;

    @Column(name = "ALGORITHM")
    private String algorithm;

    @Column(name = "OPERATION_TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    @Column(name = "RESULT", nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditResult result;

    @Column(name = "FAILED_STEP")
    private String failedStep;

    @Column(name = "ERROR_CODE")
    private String errorCode;

    @Column(name = "ERROR_MESSAGE", columnDefinition = "CLOB")
    private String errorMessage;

    @Column(name = "TIMESTAMP", nullable = false)
    private Instant timestamp;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    /**
     * Types of operations tracked by the audit log.
     */
    public enum OperationType {
        SIGN,
        VERIFY,
        CERT_REGISTER,
        CERT_VALIDATE
    }

    /**
     * Result status of an audited operation.
     */
    public enum AuditResult {
        SUCCESS,
        FAILURE
    }
}
