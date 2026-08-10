package msb.com.vn.dsign.domain.entity;

/**
 * Represents the lifecycle status of a digital certificate in the D-Sign system.
 */
public enum CertificateStatus {

    /**
     * Certificate is active and valid for signing operations.
     */
    ACTIVE,

    /**
     * Certificate has passed its notAfter validity date.
     */
    EXPIRED,

    /**
     * Certificate has been revoked by the issuing CA.
     */
    REVOKED,

    /**
     * Certificate is temporarily suspended and cannot be used for signing.
     */
    SUSPENDED
}
