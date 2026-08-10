package msb.com.vn.qrservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.queue.ManagedQueue;
import msb.com.vn.qrservice.queue.QueueManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API theo dõi & quản lý các queue trong hệ thống.
 *
 * Dùng để monitor real-time:
 * - Queue nào đang đầy (cảnh báo trước khi drop)
 * - Số item đã xử lý / drop / lỗi
 * - Throughput của từng queue
 */
@RestController
@RequestMapping("/api/v1/queues")
@RequiredArgsConstructor
@Tag(name = "Queue Monitor", description = "Theo dõi và quản lý các async queue")
public class QueueMonitorController {

    private final QueueManager queueManager;

    @GetMapping
    @Operation(summary = "Xem metrics tất cả queue")
    public ResponseEntity<ApiResponse<List<ManagedQueue.QueueStats>>> getAllQueues() {
        return ResponseEntity.ok(ApiResponse.success(
                "Có " + queueManager.getQueueCount() + " queue đang hoạt động",
                queueManager.getAllStats()));
    }

    @GetMapping("/overview")
    @Operation(summary = "Tổng quan toàn bộ queue (dùng cho monitoring/health)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(queueManager.getOverview()));
    }

    @GetMapping("/{name}")
    @Operation(summary = "Xem metrics 1 queue theo tên")
    public ResponseEntity<ApiResponse<ManagedQueue.QueueStats>> getQueue(@PathVariable String name) {
        ManagedQueue.QueueStats stats = queueManager.getStats(name);
        if (stats == null) {
            return ResponseEntity.ok(ApiResponse.error("QUEUE_NOT_FOUND",
                    "Không tìm thấy queue: " + name));
        }
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
