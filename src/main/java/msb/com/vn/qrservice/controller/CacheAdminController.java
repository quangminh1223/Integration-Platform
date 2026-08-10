package msb.com.vn.qrservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.cache.DbTableCache;
import msb.com.vn.qrservice.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API admin để nạp lại (refresh) cache hệ thống từ DB lên Redis.
 *
 * Dùng khi: bạn vừa sửa dữ liệu trong bảng cấu hình ở DB và muốn Redis cập nhật NGAY,
 * không phải chờ scheduler hay restart app.
 *
 * <b>CẢNH BÁO BẢO MẬT:</b> các endpoint này CHƯA có xác thực/phân quyền. Chúng cho phép
 * đọc toàn bộ bảng và xóa cache. Cần đặt sau lớp auth (API gateway / Spring Security)
 * hoặc giới hạn IP nội bộ trước khi lên production.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/cache")
@RequiredArgsConstructor
@Tag(name = "Cache Admin", description = "Refresh cache hệ thống (DB → Redis) thủ công")
public class CacheAdminController {

    private final DbTableCache dbTableCache;

    // ── 1. Reload 1 bảng ──────────────────────────────────────────────────────

    @PostMapping("/reload/{table}")
    @Operation(summary = "Nạp lại 1 bảng từ DB lên Redis",
            description = "Đọc toàn bộ bảng và ghi đè cache. keyColumn là cột làm khóa (vd SPF_CODE).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reloadTable(
            @Parameter(description = "Tên bảng, vd ESB_SRV_PROFILE", required = true)
            @PathVariable String table,
            @Parameter(description = "Cột làm khóa, vd SPF_CODE", required = true)
            @RequestParam String keyColumn) {

        if (!dbTableCache.isEnabled()) {
            return redisDisabled();
        }
        try {
            int count = dbTableCache.reload(table, keyColumn);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", table);
            data.put("keyColumn", keyColumn);
            data.put("cachedRows", count);
            log.info("Admin reload cache: table={}, key={}, rows={}", table, keyColumn, count);
            return ResponseEntity.ok(ApiResponse.success(
                    "Đã nạp " + count + " dòng của bảng " + table, data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_INPUT", e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi reload cache bảng {}: {}", table, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("RELOAD_FAILED", "Lỗi nạp cache: " + e.getMessage()));
        }
    }

    // ── 2. Reload nhiều bảng cùng lúc ─────────────────────────────────────────

    @PostMapping("/reload")
    @Operation(summary = "Nạp lại nhiều bảng cùng lúc",
            description = """
                Body là danh sách bảng + cột khóa:
                ```json
                [
                  {"table": "ESB_SRV_PROFILE", "keyColumn": "SPF_CODE"},
                  {"table": "ESB_AUTHORIZED",  "keyColumn": "ATZ_ID"}
                ]
                ```
                """)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> reloadTables(
            @RequestBody List<ReloadRequest> requests) {

        if (!dbTableCache.isEnabled()) {
            return redisDisabled();
        }
        List<Map<String, Object>> results = requests.stream().map(req -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("table", req.table());
            r.put("keyColumn", req.keyColumn());
            try {
                int count = dbTableCache.reload(req.table(), req.keyColumn());
                r.put("cachedRows", count);
                r.put("success", true);
            } catch (Exception e) {
                r.put("success", false);
                r.put("error", e.getMessage());
                log.error("Lỗi reload bảng {}: {}", req.table(), e.getMessage());
            }
            return r;
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(
                "Đã xử lý " + results.size() + " bảng", results));
    }

    // ── 3. Xóa cache 1 bảng ───────────────────────────────────────────────────

    @DeleteMapping("/{table}")
    @Operation(summary = "Xóa cache của 1 bảng trên Redis")
    public ResponseEntity<ApiResponse<Void>> evict(@PathVariable String table) {
        if (!dbTableCache.isEnabled()) {
            return redisDisabled();
        }
        dbTableCache.evict(table);
        log.info("Admin evict cache: table={}", table);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa cache bảng " + table, null));
    }

    private <T> ResponseEntity<ApiResponse<T>> redisDisabled() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("REDIS_DISABLED",
                        "Redis cache chưa bật — set system-cache.redis.enabled=true và cấu hình kết nối Redis"));
    }

    /** Item trong request reload nhiều bảng. */
    public record ReloadRequest(String table, String keyColumn) {}
}
