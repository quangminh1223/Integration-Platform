package msb.com.vn.integration.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for retry and batch operations.
 * - Retries failed messages from dead-letter queue
 * - Runs periodic health checks on adapters
 * - Triggers scheduled flows (cron-based)
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RetryScheduler {

    /**
     * Retry failed messages every 60 seconds.
     */
    @Scheduled(fixedDelayString = "${integration.scheduler.retry-interval-ms:60000}")
    public void retryFailedMessages() {
        log.debug("Running retry scheduler...");
        // TODO: Pull from DLQ and re-submit to FlowEngine
    }

    /**
     * Adapter health check every 30 seconds.
     */
    @Scheduled(fixedDelayString = "${integration.scheduler.health-check-interval-ms:30000}")
    public void adapterHealthCheck() {
        log.debug("Running adapter health check...");
        // TODO: Check all registered adapters via PluginRegistry
    }

    /**
     * Config refresh every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${integration.scheduler.config-refresh-interval-ms:300000}")
    public void refreshConfig() {
        log.debug("Running config refresh...");
        // TODO: Reload hot config from Redis
    }
}
