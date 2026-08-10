package msb.com.vn.dsign.exception;

/**
 * Thrown when a certificate is invalid for use — e.g., expired, revoked, or malformed.
 */
public class CertificateInvalidException extends DSignException {

    /**
     * Reasons a certificate can be invalid.
     */
    public enum Reason {
        EXPIRED,
        REVOKED,
        MALFORMED
    }

    private final Reason reason;

    public CertificateInvalidException(Reason reason) {
        super(String.format("Certificate is invalid: %s", reason));
        this.reason = reason;
    }

    public CertificateInvalidException(Reason reason, String details) {
        super(String.format("Certificate is invalid: %s — %s", reason, details));
        this.reason = reason;
    }

    public CertificateInvalidException(Reason reason, Throwable cause) {
        super(String.format("Certificate is invalid: %s", reason), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
