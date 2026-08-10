package msb.com.vn.dsign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for digital signing operations.
 * Contains all required input data to perform a digital signature.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigningRequest {

    /**
     * Client-provided idempotency key to prevent duplicate processing.
     */
    @NotBlank(message = "requestId is mandatory")
    private String requestId;

    /**
     * Base64-encoded document data to be signed.
     */
    @NotBlank(message = "documentData is mandatory")
    private String documentData;

    /**
     * Signer identity reference.
     */
    @NotBlank(message = "signerId is mandatory")
    private String signerId;

    /**
     * Reference to a registered certificate in the D-Sign system.
     */
    @NotBlank(message = "certificateRef is mandatory")
    private String certificateRef;

    /**
     * Signing algorithm to use (e.g., "SHA256withRSA", "SHA512withRSA").
     */
    @NotBlank(message = "algorithm is mandatory")
    private String algorithm;

    /**
     * Optional flag to include a signing timestamp in the response.
     */
    private boolean includeTimestamp;
}
