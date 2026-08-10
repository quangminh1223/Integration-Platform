package msb.com.vn.dsign.exception;

/**
 * Thrown when the HSM is unreachable, the connection pool is exhausted,
 * or the request queue has exceeded its maximum depth.
 */
public class HsmUnavailableException extends DSignException {

    private final String slotIdentifier;

    public HsmUnavailableException(String message) {
        super(message);
        this.slotIdentifier = null;
    }

    public HsmUnavailableException(String message, String slotIdentifier) {
        super(message);
        this.slotIdentifier = slotIdentifier;
    }

    public HsmUnavailableException(String message, String slotIdentifier, Throwable cause) {
        super(message, cause);
        this.slotIdentifier = slotIdentifier;
    }

    public HsmUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.slotIdentifier = null;
    }

    public String getSlotIdentifier() {
        return slotIdentifier;
    }
}
