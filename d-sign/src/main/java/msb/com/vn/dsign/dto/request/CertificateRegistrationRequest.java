package msb.com.vn.dsign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for registering a digital certificate with the D-Sign system.
 * Contains the certificate data, owner identity, CA provider, and HSM key reference.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateRegistrationRequest {

    /**
     * PEM or Base64-encoded X.509 certificate data.
     */
    @NotBlank(message = "certificateData is mandatory")
    private String certificateData;

    /**
     * Certificate owner identity.
     */
    @NotBlank(message = "ownerId is mandatory")
    private String ownerId;

    /**
     * CA provider identifier (must be configured in the system).
     */
    @NotBlank(message = "caProvider is mandatory")
    private String caProvider;

    /**
     * HSM key reference for the private key associated with this certificate.
     */
    @NotBlank(message = "keyReference is mandatory")
    private String keyReference;
}
