package msb.com.vn.dsign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for signature verification operations.
 * Contains the cryptographic verification result and any warnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResponse {

    /**
     * Unique correlation ID for tracing the verification operation.
     */
    private String correlationId;

    /**
     * Cryptographic verification result: true if the signature is valid, false otherwise.
     */
    private boolean valid;

    /**
     * Warnings related to the verification (e.g., "Certificate expired").
     * Empty list when no warnings.
     */
    private List<String> warnings;
}
