package msb.com.vn.integration.core.transformer;

import msb.com.vn.integration.common.enums.ContentType;
import msb.com.vn.integration.common.model.IntegrationMessage;

/**
 * Strategy pattern for message transformation.
 * Each transformer handles a specific conversion (JSON→XML, XML→JSON, ISO8583→JSON, etc.)
 *
 * <p>Transformers are stateless and thread-safe. They are registered in the
 * {@link msb.com.vn.integration.core.plugin.PluginRegistry} and selected at runtime
 * based on the flow configuration.</p>
 */
public interface MessageTransformer {

    /**
     * Unique transformer name (used in flow config to reference this transformer).
     */
    String getName();

    /**
     * Source content type this transformer reads.
     */
    ContentType getSourceType();

    /**
     * Target content type this transformer produces.
     */
    ContentType getTargetType();

    /**
     * Transform the message payload.
     *
     * @param message the message to transform
     * @return a new message with transformed payload
     */
    IntegrationMessage transform(IntegrationMessage message);

    /**
     * Check if this transformer can handle the given message.
     */
    default boolean supports(IntegrationMessage message) {
        return message.getContentType() != null
                && message.getContentType().equalsIgnoreCase(getSourceType().name());
    }
}
