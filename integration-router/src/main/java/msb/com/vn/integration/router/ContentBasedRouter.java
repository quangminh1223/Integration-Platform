package msb.com.vn.integration.router;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.exception.RoutingException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Content-based router — routes messages based on header/payload content.
 * Routing rules are configurable and hot-reloadable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentBasedRouter implements MessageRouter {

    private final Map<String, RouteDefinition> routingTable = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "content-based-router";
    }

    @Override
    public RouteDefinition resolve(IntegrationMessage message) {
        // First: try exact match on target header
        String target = message.getTarget();
        if (target != null && routingTable.containsKey(target)) {
            log.debug("Route resolved by target: {} -> {}", target, routingTable.get(target).getEndpoint());
            return routingTable.get(target);
        }

        // Second: try header-based routing
        String routeKey = message.getHeader("X-Route-Key");
        if (routeKey != null && routingTable.containsKey(routeKey)) {
            log.debug("Route resolved by X-Route-Key: {}", routeKey);
            return routingTable.get(routeKey);
        }

        // Third: try flowId-based routing
        String flowId = message.getFlowId();
        if (flowId != null && routingTable.containsKey(flowId)) {
            log.debug("Route resolved by flowId: {}", flowId);
            return routingTable.get(flowId);
        }

        throw new RoutingException(
                "No route found for message: target=" + target + ", routeKey=" + routeKey,
                message.getCorrelationId());
    }

    /**
     * Register/update a routing entry (supports hot configuration).
     */
    public void registerRoute(String key, RouteDefinition route) {
        routingTable.put(key, route);
        log.info("Route registered: key={}, endpoint={}", key, route.getEndpoint());
    }

    /**
     * Remove a routing entry.
     */
    public void removeRoute(String key) {
        routingTable.remove(key);
        log.info("Route removed: key={}", key);
    }

    /**
     * Reload all routes from configuration.
     */
    public void reloadRoutes(List<RoutingConfig> configs) {
        routingTable.clear();
        configs.forEach(config ->
                routingTable.put(config.getRouteKey(), config.toRouteDefinition()));
        log.info("Routes reloaded: {} entries", routingTable.size());
    }

    public Map<String, RouteDefinition> getRoutingTable() {
        return Map.copyOf(routingTable);
    }
}
