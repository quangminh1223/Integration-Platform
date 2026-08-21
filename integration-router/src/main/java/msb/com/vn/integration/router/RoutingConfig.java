package msb.com.vn.integration.router;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.routing.RouteDefinition;

import java.util.Map;

/**
 * Configuration model for a routing entry (loaded from YAML/Redis).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingConfig {

    private String routeKey;
    private String adapterName;
    private String protocol;
    private String endpoint;
    private String method;
    private long timeoutMs;
    private boolean circuitBreakerEnabled;
    private Map<String, String> properties;

    public RouteDefinition toRouteDefinition() {
        return RouteDefinition.builder()
                .adapterName(adapterName)
                .protocol(ProtocolType.valueOf(protocol))
                .endpoint(endpoint)
                .method(method)
                .timeoutMs(timeoutMs > 0 ? timeoutMs : 10000)
                .circuitBreakerEnabled(circuitBreakerEnabled)
                .properties(properties)
                .build();
    }
}
