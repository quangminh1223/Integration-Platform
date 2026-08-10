package msb.com.vn.qrservice.domain.repository;

import msb.com.vn.qrservice.domain.entity.QrParseHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface QrParseHistoryRepository extends JpaRepository<QrParseHistory, String> {

    Page<QrParseHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);
}
