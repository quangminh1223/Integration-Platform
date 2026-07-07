package msb.com.vn.integration.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Universal message envelope flowing through the integration platform.
 * Every adapter, transformer, and router works with this unified model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationMessage {

    /** Unique message ID for tracking and idempotency */
    private String messageId;

    /** Correlation ID linking request-response pairs across systems */
    private String correlationId;

    /** Source system identifier */
    private String source;

    /** Target system/route identifier */
    private String target;

    /** Message payload (generic - can be JSON, XML, binary, etc.) */
    private Object payload;

    /** Message headers for routing and metadata */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /** Message properties for processing control */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /** Timestamp when message was created */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** Current processing status */
    @Builder.Default
    private MessageStatus status = MessageStatus.RECEIVED;

    /** Number of retry attempts */
    @Builder.Default
    private int retryCount = 0;

    /** Flow ID this message belongs to */
    private String flowId;

    /** Content type of payload */
    private String contentType;

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public void addProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    public String getHeader(String key) {
        return this.headers.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key) {
        return (T) this.properties.get(key);
    }
}
