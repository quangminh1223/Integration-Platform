package msb.com.vn.integration.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Metrics service — exposes Prometheus/Micrometer metrics.
 * Tracks: message count, success/fail rate, latency, adapter health.
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final Map<String, Timer> flowTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> flowCounters = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record a flow execution.
     */
    public void recordFlowExecution(String flowId, boolean success, Duration duration) {
        // Timer
        Timer timer = flowTimers.computeIfAbsent(flowId,
                id -> Timer.builder("integration.flow.duration")
                        .tag("flowId", id)
                        .register(registry));
        timer.record(duration);

        // Counter
        String counterKey = flowId + ":" + (success ? "success" : "failure");
        Counter counter = flowCounters.computeIfAbsent(counterKey,
                key -> Counter.builder("integration.flow.count")
                        .tag("flowId", flowId)
                        .tag("status", success ? "success" : "failure")
                        .register(registry));
        counter.increment();
    }

    /**
     * Record adapter call.
     */
    public void recordAdapterCall(String adapterName, boolean success, Duration duration) {
        Timer.builder("integration.adapter.duration")
                .tag("adapter", adapterName)
                .tag("status", success ? "success" : "failure")
                .register(registry)
                .record(duration);
    }

    /**
     * Record message count by protocol.
     */
    public void recordMessageReceived(String protocol) {
        Counter.builder("integration.messages.received")
                .tag("protocol", protocol)
                .register(registry)
                .increment();
    }
}
