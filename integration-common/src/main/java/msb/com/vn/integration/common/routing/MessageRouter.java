package msb.com.vn.integration.common.routing;

import msb.com.vn.integration.common.model.IntegrationMessage;

/**
 * Strategy pattern for message routing.
 * Determines which adapter/target system should receive the message.
 *
 * <p>Declared in {@code integration-common} so that {@code integration-core} can depend on the
 * routing <b>abstraction</b> without depending on {@code integration-router}, which supplies the
 * implementation. That is what makes the Strategy pattern real here: swapping the routing
 * algorithm means adding a class in the router module, with no change to core.</p>
 */
public interface MessageRouter {

    /**
     * Unique router name (referenced from flow config).
     */
    String getName();

    /**
     * Resolve the target route for the given message.
     *
     * @param message the message to route
     * @return resolved route definition
     */
    RouteDefinition resolve(IntegrationMessage message);
}
