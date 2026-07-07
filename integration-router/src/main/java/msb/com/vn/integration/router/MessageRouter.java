package msb.com.vn.integration.router;

import msb.com.vn.integration.common.model.IntegrationMessage;

/**
 * Strategy pattern for message routing.
 * Determines which adapter/target system should receive the message.
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
