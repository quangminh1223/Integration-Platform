package msb.com.vn.dsign.exception;

/**
 * Base exception for all D-Sign module errors.
 * All domain-specific exceptions in this module extend this class.
 */
public class DSignException extends RuntimeException {

    public DSignException(String message) {
        super(message);
    }

    public DSignException(String message, Throwable cause) {
        super(message, cause);
    }
}
