package msb.com.vn.dsign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import msb.com.vn.dsign.domain.entity.CertificateStatus;

import java.time.Instant;

/**
 * Response DTO for certificate queries and registration results.
 * Contains full certificate metadata and current validity status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateResponse {

    /**
     * Unique certificate reference identifier.
     */
    private String certRefId;

    /**
     * Certificate subject (e.g., "CN=Nguyen Van A, O=MSB, C=VN").
     */
    private String subject;

    /**
     * Certificate issuer (e.g., "CN=VNCA Root CA, O=VNCA, C=VN").
     */
    private String issuer;

    /**
     * Certificate serial number.
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
     * Base64-encoded public key from the certificate.
     */
    private String publicKeyBase64;

    /**
     * HSM key reference for the associated private key.
     */
    private String keyReference;

    /**
     * Certificate owner identity.
     */
    private String ownerId;

    /**
     * CA provider that issued the certificate.
     */
    private String caProvider;

    /**
     * Current certificate status (ACTIVE, EXPIRED, REVOKED, SUSPENDED).
     */
    private CertificateStatus status;
}
