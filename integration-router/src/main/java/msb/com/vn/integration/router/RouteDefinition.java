package msb.com.vn.integration.router;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import msb.com.vn.integration.common.enums.ProtocolType;

import java.util.Map;

/**
 * Defines a resolved route — where and how to send the message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDefinition {

    /** Target adapter name */
    private String adapterName;

    /** Protocol type */
    private ProtocolType protocol;

    /** Target endpoint URL or address */
    private String endpoint;

    /** HTTP method (for REST) or operation identifier */
    private String method;

    /** Additional routing properties */
    private Map<String, String> properties;

    /** Timeout in milliseconds */
    @Builder.Default
    private long timeoutMs = 10000;

    /** Whether to use circuit breaker */
    @Builder.Default
    private boolean circuitBreakerEnabled = true;
}
