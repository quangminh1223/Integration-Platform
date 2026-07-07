package msb.com.vn.integration.common.exception;

/**
 * Thrown when an adapter fails to communicate with an external system.
 */
public class AdapterException extends IntegrationException {

    private final String adapterName;

    public AdapterException(String adapterName, String message, String correlationId, boolean retryable) {
        super(message, "ADAPTER_ERROR", correlationId, retryable);
        this.adapterName = adapterName;
    }

    public AdapterException(String adapterName, String message, String correlationId, boolean retryable, Throwable cause) {
        super(message, "ADAPTER_ERROR", correlationId, retryable, cause);
        this.adapterName = adapterName;
    }

    public String getAdapterName() {
        return adapterName;
    }
}
