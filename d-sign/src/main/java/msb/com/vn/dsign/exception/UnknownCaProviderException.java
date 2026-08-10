package msb.com.vn.dsign.exception;

/**
 * Thrown when a certificate registration or operation references a CA provider
 * that is not configured in the system.
 */
public class UnknownCaProviderException extends DSignException {

    private final String providerName;

    public UnknownCaProviderException(String providerName) {
        super(String.format("Unknown CA provider: '%s'", providerName));
        this.providerName = providerName;
    }

    public UnknownCaProviderException(String providerName, Throwable cause) {
        super(String.format("Unknown CA provider: '%s'", providerName), cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
