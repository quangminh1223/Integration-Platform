package msb.com.vn.dsign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for digital signing operations.
 * Contains the generated signature and signing metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigningResponse {

    /**
     * Unique correlation ID for tracing the signing flow.
     */
    private String correlationId;

    /**
     * Base64-encoded digital signature value.
     */
    private String signatureValue;

    /**
     * Reference to the certificate used for signing.
     */
    private String certificateRef;

    /**
     * Algorithm applied during the signing operation.
     */
    private String algorithm;

    /**
     * Timestamp when the signing operation occurred.
     */
    private Instant signingTimestamp;
}
