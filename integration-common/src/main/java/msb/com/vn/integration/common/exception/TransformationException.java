package msb.com.vn.integration.common.exception;

/**
 * Thrown when message transformation fails.
 */
public class TransformationException extends IntegrationException {

    public TransformationException(String message, String correlationId) {
        super(message, "TRANSFORM_ERROR", correlationId, false);
    }

    public TransformationException(String message, String correlationId, Throwable cause) {
        super(message, "TRANSFORM_ERROR", correlationId, false, cause);
    }
}
