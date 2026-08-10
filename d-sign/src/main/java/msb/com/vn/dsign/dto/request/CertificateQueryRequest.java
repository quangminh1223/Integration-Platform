package msb.com.vn.dsign.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for querying certificates.
 * Used as search criteria when looking up certificates in the D-Sign system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateQueryRequest {

    /**
     * Certificate reference identifier to look up.
     */
    private String certRefId;

    /**
     * Filter by certificate owner.
     */
    private String ownerId;

    /**
     * Filter by CA provider.
     */
    private String caProvider;

    /**
     * Filter by certificate status (ACTIVE, EXPIRED, REVOKED, SUSPENDED).
     */
    private String status;
}
