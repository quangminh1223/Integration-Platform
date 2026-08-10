package msb.com.vn.dsign.exception;

/**
 * Thrown when an external CA provider returns an error during certificate issuance,
 * renewal, or validation operations.
 */
public class CaProviderException extends DSignException {

    private final String providerName;
    private final String errorCode;
    private final String errorDescription;

    public CaProviderException(String providerName, String errorCode, String errorDescription) {
        super(String.format("CA provider '%s' error [%s]: %s", providerName, errorCode, errorDescription));
        this.providerName = providerName;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public CaProviderException(String providerName, String errorCode, String errorDescription, Throwable cause) {
        super(String.format("CA provider '%s' error [%s]: %s", providerName, errorCode, errorDescription), cause);
        this.providerName = providerName;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }
}
