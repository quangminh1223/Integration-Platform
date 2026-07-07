package msb.com.vn.integration.common.model;

/**
 * Lifecycle states of an IntegrationMessage as it flows through the platform.
 */
public enum MessageStatus {
    RECEIVED,
    VALIDATING,
    TRANSFORMING,
    ROUTING,
    DISPATCHING,
    DELIVERED,
    FAILED,
    RETRYING,
    DEAD_LETTER
}
