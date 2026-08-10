package msb.com.vn.dsign.mapping;

import msb.com.vn.dsign.domain.entity.Certificate;
import msb.com.vn.dsign.dto.request.CertificateRegistrationRequest;
import msb.com.vn.dsign.dto.response.CertificateResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Certificate entity ↔ DTO conversion.
 */
@Mapper(componentModel = "spring")
public interface CertificateMapper {

    /**
     * Convert a Certificate entity to a CertificateResponse DTO.
     *
     * @param entity the certificate entity
     * @return the response DTO
     */
    CertificateResponse toResponse(Certificate entity);

    /**
     * Convert a CertificateRegistrationRequest DTO to a Certificate entity.
     * Fields not present in the request (certRefId, subject, issuer, serialNumber, etc.)
     * are ignored and should be set by the service layer after parsing the certificate.
     *
     * @param request the registration request
     * @return the certificate entity (partially populated)
     */
    @Mapping(target = "certRefId", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "issuer", ignore = true)
    @Mapping(target = "serialNumber", ignore = true)
    @Mapping(target = "notBefore", ignore = true)
    @Mapping(target = "notAfter", ignore = true)
    @Mapping(target = "publicKeyBase64", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Certificate toEntity(CertificateRegistrationRequest request);
}
