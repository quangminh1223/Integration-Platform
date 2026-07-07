package msb.com.vn.integration.common.exception;

/**
 * Thrown when no route can be resolved for a message.
 */
public class RoutingException extends IntegrationException {

    public RoutingException(String message, String correlationId) {
        super(message, "ROUTING_ERROR", correlationId, false);
    }
}
