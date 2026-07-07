package msb.com.vn.integration.monitoring;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.model.IntegrationMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log service — records all integration events for compliance and debugging.
 * Logs are structured JSON for easy ingestion by ELK/Splunk.
 */
@Slf4j
@Service
public class AuditLogService {

    /**
     * Log an integration event.
     */
    public void logEvent(String eventType, IntegrationMessage message, Map<String, Object> metadata) {
        log.info("AUDIT | event={} | messageId={} | correlationId={} | flowId={} | source={} | target={} | status={} | metadata={}",
                eventType,
                message.getMessageId(),
                message.getCorrelationId(),
                message.getFlowId(),
                message.getSource(),
                message.getTarget(),
                message.getStatus(),
                metadata);
    }

    /**
     * Log request received.
     */
    public void logRequest(IntegrationMessage message) {
        logEvent("REQUEST_RECEIVED", message, Map.of("timestamp", Instant.now()));
    }

    /**
     * Log successful delivery.
     */
    public void logSuccess(IntegrationMessage message, long durationMs) {
        logEvent("DELIVERY_SUCCESS", message, Map.of(
                "durationMs", durationMs,
                "timestamp", Instant.now()
        ));
    }

    /**
     * Log failure.
     */
    public void logFailure(IntegrationMessage message, String errorCode, String errorMessage) {
        logEvent("DELIVERY_FAILED", message, Map.of(
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", Instant.now()
        ));
    }
}
