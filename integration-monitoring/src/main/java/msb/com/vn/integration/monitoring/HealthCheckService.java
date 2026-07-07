package msb.com.vn.integration.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import msb.com.vn.integration.core.plugin.PluginRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom health indicator that reports health of all registered adapters.
 * Exposed via /actuator/health endpoint.
 */
@Slf4j
@Component("integrationAdapters")
@RequiredArgsConstructor
public class HealthCheckService implements HealthIndicator {

    private final PluginRegistry pluginRegistry;

    @Override
    public Health health() {
        Map<String, IntegrationAdapter> adapters = pluginRegistry.getAllAdapters();
        Map<String, Object> details = new HashMap<>();
        boolean allHealthy = true;

        for (Map.Entry<String, IntegrationAdapter> entry : adapters.entrySet()) {
            String name = entry.getKey();
            IntegrationAdapter adapter = entry.getValue();

            try {
                boolean healthy = adapter.isHealthy();
                details.put(name, Map.of(
                        "status", healthy ? "UP" : "DOWN",
                        "protocol", adapter.getProtocol().name()
                ));
                if (!healthy) {
                    allHealthy = false;
                }
            } catch (Exception e) {
                details.put(name, Map.of(
                        "status", "DOWN",
                        "error", e.getMessage()
                ));
                allHealthy = false;
            }
        }

        details.put("totalAdapters", adapters.size());

        if (allHealthy) {
            return Health.up().withDetails(details).build();
        } else {
            return Health.down().withDetails(details).build();
        }
    }
}
