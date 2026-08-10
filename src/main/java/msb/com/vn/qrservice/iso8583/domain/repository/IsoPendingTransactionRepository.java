package msb.com.vn.qrservice.iso8583.domain.repository;

import msb.com.vn.qrservice.common.enums.IsoPendingStatus;
import msb.com.vn.qrservice.iso8583.domain.entity.IsoPendingTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn giao dịch ISO8583 đang chờ đối soát.
 */
public interface IsoPendingTransactionRepository extends JpaRepository<IsoPendingTransaction, String> {

    /**
     * Tìm giao dịch theo định danh ISO8583 trong ngày (STAN duy nhất theo terminal trong ngày).
     */
    Optional<IsoPendingTransaction> findByStanAndTerminalIdAndLocalTxnDate(
            String stan, String terminalId, String localTxnDate);

    Optional<IsoPendingTransaction> findByCorrelationId(String correlationId);

    /**
     * Danh sách giao dịch theo trạng thái, cũ nhất trước.
     */
    List<IsoPendingTransaction> findByStatusInOrderByCreatedAtAsc(
            Collection<IsoPendingStatus> statuses, Pageable pageable);

    /**
     * Giao dịch còn treo và đã quá mốc thời gian theo dõi — cần chuyển sang xử lý thủ công.
     */
    List<IsoPendingTransaction> findByStatusAndCreatedAtBefore(
            IsoPendingStatus status, LocalDateTime before, Pageable pageable);

    long countByStatus(IsoPendingStatus status);

    /**
     * Đếm theo từng trạng thái trong một lần truy vấn — dùng cho metrics.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT t.status, COUNT(t) FROM IsoPendingTransaction t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    /**
     * Tổng số tiền đang treo theo trạng thái — dùng cho báo cáo lệch quỹ.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(t) FROM IsoPendingTransaction t "
                    + "WHERE t.status = :status AND t.createdAt >= :from")
    long countByStatusSince(@Param("status") IsoPendingStatus status,
                            @Param("from") LocalDateTime from);
}
