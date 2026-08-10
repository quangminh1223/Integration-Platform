package msb.com.vn.dsign.ca.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result model returned by a CA provider after a certificate issuance attempt.
 * Contains either the issued certificate data or error details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateIssuanceResult {

    /**
     * PEM or Base64-encoded certificate data (null on failure).
     */
    private String certificateData;

    /**
     * Certificate subject DN.
     */
    private String subject;

    /**
     * Certificate issuer DN.
     */
    private String issuer;

    /**
     * Certificate serial number assigned by the CA.
     */
    private String serialNumber;

    /**
     * Certificate validity start time.
     */
    private Instant notBefore;

    /**
     * Certificate validity end time.
     */
    private Instant notAfter;

    /**
     * Whether the issuance was successful.
     */
    private boolean success;

    /**
     * CA-specific error code (null on success).
     */
    private String errorCode;

    /**
     * Human-readable error message from the CA (null on success).
     */
    private String errorMessage;
}
