package msb.com.vn.integration.core.flow;

import msb.com.vn.integration.common.model.IntegrationMessage;

/**
 * A single step in a processing flow (Chain of Responsibility pattern).
 * Each step receives the message, performs its logic, and passes to the next step.
 */
public interface FlowStep {

    /**
     * Unique step identifier (for logging and config).
     */
    String getStepName();

    /**
     * Execution order (lower = earlier).
     */
    int getOrder();

    /**
     * Process the message. Return the (possibly modified) message for the next step.
     * Throw an exception to abort the chain.
     */
    IntegrationMessage execute(IntegrationMessage message, FlowContext context);

    /**
     * Whether this step should be skipped for the given message.
     */
    default boolean shouldSkip(IntegrationMessage message, FlowContext context) {
        return false;
    }
}
