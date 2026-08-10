package msb.com.vn.dsign.domain.repository;

import msb.com.vn.dsign.domain.entity.Certificate;
import msb.com.vn.dsign.domain.entity.CertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Certificate} entities.
 */
@Repository
public interface CertificateRepository extends JpaRepository<Certificate, String> {

    Optional<Certificate> findByCertRefId(String certRefId);

    List<Certificate> findByOwnerId(String ownerId);

    List<Certificate> findByStatus(CertificateStatus status);
}
