package msb.com.vn.dsign.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kafka event payload for signing audit trail.
 * Emitted to the dsign.audit.events topic after every signing operation.
 * Contains all required fields for compliance and incident investigation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigningAuditEvent {

    /**
     * Correlation ID linking this audit event to the signing flow.
     */
    private String correlationId;

    /**
     * Identity of the signer who initiated the operation.
     */
    private String signerId;

    /**
     * Reference to the certificate used in the operation.
     */
    private String certificateRef;

    /**
     * Signing algorithm applied.
     */
    private String algorithm;

    /**
     * Timestamp when the operation occurred.
     */
    private Instant timestamp;

    /**
     * Operation result: "SUCCESS" or "FAILURE".
     */
    private String result;

    /**
     * Error code when the operation failed. Null on success.
     */
    private String errorCode;

    /**
     * Error message when the operation failed. Null on success.
     */
    private String errorMessage;

    /**
     * Name of the flow step that failed. Null on success.
     */
    private String failedStep;
}
