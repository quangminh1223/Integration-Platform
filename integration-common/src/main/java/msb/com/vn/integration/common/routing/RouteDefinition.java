package msb.com.vn.integration.common.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import msb.com.vn.integration.common.enums.ProtocolType;

import java.util.Map;

/**
 * Defines a resolved route — where and how to send the message.
 *
 * <p>Lives in {@code integration-common}, not {@code integration-router}, because this is a
 * <b>contract</b> rather than an implementation. {@code integration-core} needs it in
 * {@code RoutingStep} and {@code DispatchStep}, while {@code integration-router} depends on
 * {@code integration-core} — keeping the type in the router module made the two modules
 * mutually dependent and {@code integration-core} could not compile at all.</p>
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
