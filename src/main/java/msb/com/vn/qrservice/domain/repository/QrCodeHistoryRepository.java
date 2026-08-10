package msb.com.vn.qrservice.domain.repository;

import msb.com.vn.qrservice.common.enums.QrType;
import msb.com.vn.qrservice.domain.entity.QrCodeHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QrCodeHistoryRepository extends JpaRepository<QrCodeHistory, String> {

    Page<QrCodeHistory> findByQrTypeOrderByCreatedAtDesc(QrType qrType, Pageable pageable);

    Page<QrCodeHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    List<QrCodeHistory> findTop10ByOrderByCreatedAtDesc();
}
