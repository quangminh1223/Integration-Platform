package msb.com.vn.dsign.ca.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result model returned after validating a certificate's status via OCSP or CRL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateValidationResult {

    /**
     * The validation status of the certificate.
     */
    private Status status;

    /**
     * The validation method used (e.g., "OCSP", "CRL").
     */
    private String validationMethod;

    /**
     * Timestamp when the validation was performed.
     */
    private Instant validatedAt;

    /**
     * Error message if validation could not be completed (null on success).
     */
    private String errorMessage;

    /**
     * Possible validation statuses mapped from OCSP/CRL responses.
     */
    public enum Status {
        /**
         * Certificate is valid and not revoked (OCSP: good).
         */
        VALID,

        /**
         * Certificate has been revoked by the CA (OCSP: revoked).
         */
        REVOKED,

        /**
         * Certificate status could not be determined (OCSP: unknown).
         */
        UNKNOWN
    }
}
