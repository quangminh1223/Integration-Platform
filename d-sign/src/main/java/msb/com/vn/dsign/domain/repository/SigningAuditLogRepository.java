package msb.com.vn.dsign.domain.repository;

import msb.com.vn.dsign.domain.entity.SigningAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SigningAuditLog} entities.
 */
@Repository
public interface SigningAuditLogRepository extends JpaRepository<SigningAuditLog, Long> {

    List<SigningAuditLog> findByCorrelationId(String correlationId);
}
