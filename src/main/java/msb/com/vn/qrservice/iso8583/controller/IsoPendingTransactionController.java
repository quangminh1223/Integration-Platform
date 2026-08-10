package msb.com.vn.qrservice.iso8583.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.enums.IsoPendingStatus;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.iso8583.domain.entity.IsoPendingTransaction;
import msb.com.vn.qrservice.iso8583.domain.repository.IsoPendingTransactionRepository;
import msb.com.vn.qrservice.iso8583.pending.PendingTransactionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * API tra soát giao dịch ISO8583 bị timeout, phục vụ vận hành và đối soát.
 *
 * <p>CORE không hỗ trợ bản tin đảo 0400 nên các giao dịch này phải được xử lý thủ công.
 * Đây là công cụ để nghiệp vụ nhìn thấy và đóng từng khoản.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/iso8583/pending")
@RequiredArgsConstructor
@Tag(name = "ISO8583 Pending", description = "Tra soát giao dịch ISO8583 timeout chờ đối soát")
public class IsoPendingTransactionController {

    private static final int MAX_PAGE_SIZE = 500;

    private final IsoPendingTransactionRepository repository;
    private final PendingTransactionService pendingTransactionService;

    @GetMapping
    @Operation(summary = "Danh sách giao dịch treo theo trạng thái",
            description = "CORE_CONFIRMED = lệch quỹ thật (CORE đã hạch toán, bên gọi nhận RC 68). "
                    + "NEEDS_MANUAL = cần tra soát với CORE.")
    public ResponseEntity<ApiResponse<List<IsoPendingTransaction>>> list(
            @RequestParam(required = false) IsoPendingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int safeSize = Math.min(size, MAX_PAGE_SIZE);

        List<IsoPendingStatus> statuses = status != null
                ? List.of(status)
                : List.of(IsoPendingStatus.TIMEOUT_UNKNOWN,
                          IsoPendingStatus.CORE_CONFIRMED,
                          IsoPendingStatus.NEEDS_MANUAL);

        List<IsoPendingTransaction> result = repository.findByStatusInOrderByCreatedAtAsc(
                statuses, PageRequest.of(page, safeSize));

        return ResponseEntity.ok(ApiResponse.success(
                "Tìm thấy " + result.size() + " giao dịch", result));
    }

    @GetMapping("/summary")
    @Operation(summary = "Tổng hợp số lượng giao dịch treo theo trạng thái")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary() {
        Map<IsoPendingStatus, Long> counts = new EnumMap<>(IsoPendingStatus.class);
        for (IsoPendingStatus status : IsoPendingStatus.values()) {
            counts.put(status, repository.countByStatus(status));
        }

        Map<String, Object> body = Map.of(
                "counts", counts,
                "fundDiscrepancy", counts.get(IsoPendingStatus.CORE_CONFIRMED),
                "awaitingManualCheck", counts.get(IsoPendingStatus.NEEDS_MANUAL),
                "stillUnknown", counts.get(IsoPendingStatus.TIMEOUT_UNKNOWN),
                "note", "CORE_CONFIRMED là lệch quỹ thật: CORE đã hạch toán nhưng bên gọi nhận RC 68"
        );
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/{id}/reconcile")
    @Operation(summary = "Đánh dấu đã đối soát xong",
            description = "Dùng sau khi nghiệp vụ đã xử lý khoản lệch (điều chỉnh hoặc xác nhận không cần)")
    public ResponseEntity<ApiResponse<Map<String, String>>> reconcile(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String note = body.getOrDefault("note", "");
        if (note.isBlank()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_REQUEST",
                            "Bắt buộc có 'note' mô tả cách xử lý khoản lệch"));
        }

        pendingTransactionService.markReconciled(id, note);
        log.info("Giao dịch treo id={} đã được đánh dấu đối soát xong", id);

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("id", id, "status", IsoPendingStatus.RECONCILED.name())));
    }
}
