package msb.com.vn.dsign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for signature verification operations.
 * Contains the original document data, signature, and certificate reference
 * needed to verify a digital signature.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyRequest {

    /**
     * Original document data (Base64-encoded) that was signed.
     */
    @NotBlank(message = "documentData is mandatory")
    private String documentData;

    /**
     * Base64-encoded signature value to verify.
     */
    @NotBlank(message = "signatureValue is mandatory")
    private String signatureValue;

    /**
     * Reference to the certificate used for signing.
     */
    @NotBlank(message = "certificateRef is mandatory")
    private String certificateRef;

    /**
     * Algorithm used for the signing operation (e.g., "SHA256withRSA", "SHA512withRSA").
     */
    @NotBlank(message = "algorithm is mandatory")
    private String algorithm;
}
