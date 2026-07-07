package msb.com.vn.integration.core.idempotent;

/**
 * Idempotent message processing store.
 * Prevents duplicate processing of the same message (identified by messageId).
 * Default implementation uses Redis with configurable TTL.
 */
public interface IdempotentStore {

    /**
     * Check if messageId has already been processed or is in progress.
     */
    boolean isDuplicate(String messageId);

    /**
     * Mark message as being processed (in-flight).
     */
    void markProcessing(String messageId);

    /**
     * Mark message processing as completed successfully.
     */
    void markCompleted(String messageId);

    /**
     * Mark message as failed (allows retry).
     */
    void markFailed(String messageId);
}
