package msb.com.vn.dsign.ca.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for issuing a new certificate from a CA provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateIssuanceRequest {

    /**
     * Subject Distinguished Name (e.g., "CN=Nguyen Van A, O=MSB, C=VN").
     */
    private String subjectDN;

    /**
     * Key algorithm for the certificate (e.g., "RSA").
     */
    private String keyAlgorithm;

    /**
     * Key length in bits (e.g., 2048, 4096).
     */
    private int keyLength;

    /**
     * Number of days the certificate should be valid.
     */
    private int validityDays;

    /**
     * Reference to the certificate owner in the system.
     */
    private String ownerReference;
}
