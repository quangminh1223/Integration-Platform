package msb.com.vn.qrservice.iso8583.pending;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.enums.IsoPendingStatus;
import msb.com.vn.qrservice.iso8583.domain.entity.IsoPendingTransaction;
import msb.com.vn.qrservice.iso8583.domain.repository.IsoPendingTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Ghi bản ghi giao dịch treo xuống DB trong một transaction.
 *
 * <p>Tách thành bean riêng là bắt buộc: {@link PendingTransactionService} gọi hàm ghi
 * qua method reference từ worker của queue, nếu để {@code @Transactional} trên cùng class
 * thì Spring proxy bị bỏ qua (self-invocation) và transaction không có hiệu lực.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingTransactionWriter {

    private final IsoPendingTransactionRepository repository;

    /**
     * Ghi hoặc cập nhật bản ghi giao dịch treo.
     */
    /**
     * Giao dịch treo quá lâu không có tin từ CORE → chuyển sang cần tra soát thủ công.
     *
     * @return số bản ghi đã leo thang
     */
    @Transactional
    public int escalateStaleTransactions(long escalationMinutes, int batchSize) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(escalationMinutes);

        List<IsoPendingTransaction> stale = repository.findByStatusAndCreatedAtBefore(
                IsoPendingStatus.TIMEOUT_UNKNOWN, threshold, PageRequest.of(0, batchSize));

        if (stale.isEmpty()) {
            return 0;
        }

        for (IsoPendingTransaction txn : stale) {
            txn.setStatus(IsoPendingStatus.NEEDS_MANUAL);
            txn.setReconcileAttempts(
                    txn.getReconcileAttempts() != null ? txn.getReconcileAttempts() + 1 : 1);
            txn.setReconcileNote("CORE không trả lời sau " + escalationMinutes
                    + " phút. Cần tra soát thủ công với CORE theo STAN/RRN.");

            log.error("CẦN TRA SOÁT THỦ CÔNG — CORE không phản hồi sau {} phút. "
                            + "id={}, stan={}, terminal={}, rrn={}, amount={}, correlationId={}",
                    escalationMinutes, txn.getId(), txn.getStan(),
                    txn.getTerminalId(), txn.getRrn(), txn.getAmount(), txn.getCorrelationId());
        }

        repository.saveAll(stale);
        log.error("Đã chuyển {} giao dịch treo sang trạng thái NEEDS_MANUAL", stale.size());
        return stale.size();
    }

    @Transactional
    public void write(PendingTransactionService.PendingWrite write, boolean corePosted) {
        Optional<IsoPendingTransaction> existing = findExisting(write);

        if (write.lateResponse) {
            IsoPendingTransaction entity = existing.orElseGet(() -> toEntity(write));
            entity.setCoreResponseCode(write.coreResponseCode);
            entity.setCoreRespondedAt(LocalDateTime.now());
            entity.setCoreElapsedMs(write.elapsedMs);
            entity.setStatus(corePosted
                    ? IsoPendingStatus.CORE_CONFIRMED
                    : IsoPendingStatus.CORE_DECLINED);
            repository.save(entity);
            log.info("Đã cập nhật kết quả CORE trả về muộn: stan={}, coreRC={}, status={}",
                    write.stan, write.coreResponseCode, entity.getStatus());
            return;
        }

        if (existing.isPresent()) {
            log.debug("Bản ghi treo đã tồn tại cho stan={}, bỏ qua ghi trùng", write.stan);
            return;
        }
        repository.save(toEntity(write));
    }

    private Optional<IsoPendingTransaction> findExisting(PendingTransactionService.PendingWrite write) {
        if (write.stan != null && write.terminalId != null && write.localTxnDate != null) {
            Optional<IsoPendingTransaction> byStan = repository
                    .findByStanAndTerminalIdAndLocalTxnDate(
                            write.stan, write.terminalId, write.localTxnDate);
            if (byStan.isPresent()) {
                return byStan;
            }
        }
        return repository.findByCorrelationId(write.correlationId);
    }

    private IsoPendingTransaction toEntity(PendingTransactionService.PendingWrite write) {
        return IsoPendingTransaction.builder()
                .correlationId(write.correlationId)
                .mti(write.mti)
                .stan(write.stan)
                .terminalId(write.terminalId)
                .rrn(write.rrn)
                .localTxnDate(write.localTxnDate)
                .panMasked(write.panMasked)
                .amount(write.amount)
                .currencyCode(write.currencyCode)
                .processingCode(write.processingCode)
                .remoteAddress(write.remoteAddress)
                .status(IsoPendingStatus.TIMEOUT_UNKNOWN)
                .responseSent(write.responseSent)
                .deadlineMs(write.deadlineMs)
                .reconcileAttempts(0)
                .build();
    }
}
