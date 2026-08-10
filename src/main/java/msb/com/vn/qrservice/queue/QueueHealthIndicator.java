package msb.com.vn.qrservice.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator cho queue — tích hợp Spring Actuator.
 *
 * Truy cập: GET /api/actuator/health
 *
 * Status:
 * - UP      : tất cả queue < 80% capacity
 * - WARNING : có queue > 80% (chi tiết trong details)
 * - DOWN    : có queue > 95% (sắp drop) hoặc có queue không chạy
 */
@Component("queues")
@RequiredArgsConstructor
public class QueueHealthIndicator implements HealthIndicator {

    private final QueueManager queueManager;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean down = false;
        boolean warning = false;

        for (ManagedQueue.QueueStats stats : queueManager.getAllStats()) {
            double usage = stats.usagePercent();
            String status;

            if (!stats.running()) {
                status = "DOWN (not running)";
                down = true;
            } else if (usage > 95) {
                status = "DOWN (near full)";
                down = true;
            } else if (usage > 80) {
                status = "WARNING (high usage)";
                warning = true;
            } else {
                status = "UP";
            }

            details.put(stats.name(), Map.of(
                    "status", status,
                    "usage", String.format("%.1f%%", usage),
                    "size", stats.currentSize() + "/" + stats.capacity(),
                    "processed", stats.processed(),
                    "dropped", stats.dropped(),
                    "errors", stats.errors()
            ));
        }

        Health.Builder builder = down ? Health.down()
                : warning ? Health.status("WARNING")
                : Health.up();

        return builder.withDetails(details).build();
    }
}
