package msb.com.vn.integration.core.adapter;

import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.model.IntegrationMessage;

/**
 * Common interface that EVERY adapter MUST implement.
 * This is the Adapter Pattern contract — each protocol (REST, SOAP, Kafka, etc.)
 * provides its own implementation.
 *
 * <p>The platform discovers adapters via Spring's component scanning and
 * registers them in the {@link msb.com.vn.integration.core.plugin.PluginRegistry}.</p>
 */
public interface IntegrationAdapter {

    /**
     * Unique adapter name for identification and configuration reference.
     */
    String getName();

    /**
     * Protocol this adapter handles.
     */
    ProtocolType getProtocol();

    /**
     * Send message to the external system.
     *
     * @param message the integration message to deliver
     * @return response message from the external system
     */
    IntegrationMessage send(IntegrationMessage message);

    /**
     * Check if this adapter can handle the given message.
     */
    boolean supports(IntegrationMessage message);

    /**
     * Health check for this adapter's connectivity.
     */
    boolean isHealthy();

    /**
     * Graceful shutdown hook.
     */
    default void shutdown() {
        // default no-op
    }
}
