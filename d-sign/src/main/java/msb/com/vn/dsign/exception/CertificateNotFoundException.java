package msb.com.vn.dsign.exception;

/**
 * Thrown when a certificate reference cannot be found in the system.
 */
public class CertificateNotFoundException extends DSignException {

    private final String certificateReference;

    public CertificateNotFoundException(String certificateReference) {
        super(String.format("Certificate not found: '%s'", certificateReference));
        this.certificateReference = certificateReference;
    }

    public CertificateNotFoundException(String certificateReference, Throwable cause) {
        super(String.format("Certificate not found: '%s'", certificateReference), cause);
        this.certificateReference = certificateReference;
    }

    public String getCertificateReference() {
        return certificateReference;
    }
}
