package msb.com.vn.dsign.ca;

import msb.com.vn.dsign.ca.model.CertificateIssuanceRequest;
import msb.com.vn.dsign.ca.model.CertificateIssuanceResult;
import msb.com.vn.dsign.ca.model.CertificateValidationResult;

import java.security.cert.X509Certificate;

/**
 * Provider-agnostic interface for Certificate Authority (CA) operations.
 * Each CA provider (e.g., VNCA, GlobalSign) implements this interface.
 *
 * <p>New CA providers can be added by creating a new implementation class
 * and registering it as a Spring bean. The {@link CaProviderRegistry} will
 * auto-discover it via Spring DI.</p>
 */
public interface CaProvider {

    /**
     * Get the unique name identifying this CA provider.
     *
     * @return provider name (e.g., "vnca", "globalsign")
     */
    String getProviderName();

    /**
     * Issue a new certificate via the CA.
     *
     * @param request the certificate issuance request containing subject DN, key params, and validity
     * @return the issuance result containing the certificate data or error details
     */
    CertificateIssuanceResult issueCertificate(CertificateIssuanceRequest request);

    /**
     * Validate certificate status via OCSP or CRL.
     *
     * @param certificate the X.509 certificate to validate
     * @return the validation result indicating VALID, REVOKED, or UNKNOWN status
     */
    CertificateValidationResult validateCertificate(X509Certificate certificate);

    /**
     * Check if this CA provider is reachable and operational.
     *
     * @return true if the provider is available for operations
     */
    boolean isAvailable();
}
