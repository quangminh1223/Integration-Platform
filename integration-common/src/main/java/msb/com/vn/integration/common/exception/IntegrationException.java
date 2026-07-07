package msb.com.vn.integration.common.exception;

import lombok.Getter;

/**
 * Base exception for all integration platform errors.
 * All module-specific exceptions extend this.
 */
@Getter
public class IntegrationException extends RuntimeException {

    private final String errorCode;
    private final String correlationId;
    private final boolean retryable;

    public IntegrationException(String message, String errorCode, String correlationId, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
        this.retryable = retryable;
    }

    public IntegrationException(String message, String errorCode, String correlationId, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
        this.retryable = retryable;
    }
}
