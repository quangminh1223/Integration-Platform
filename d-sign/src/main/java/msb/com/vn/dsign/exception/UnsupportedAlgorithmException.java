package msb.com.vn.dsign.exception;

import java.util.List;

/**
 * Thrown when a signing request specifies an algorithm not in the configured supported set.
 */
public class UnsupportedAlgorithmException extends DSignException {

    private final String unsupportedAlgorithm;
    private final List<String> supportedAlgorithms;

    public UnsupportedAlgorithmException(String unsupportedAlgorithm, List<String> supportedAlgorithms) {
        super(String.format("Algorithm '%s' is not supported. Supported algorithms: %s",
                unsupportedAlgorithm, supportedAlgorithms));
        this.unsupportedAlgorithm = unsupportedAlgorithm;
        this.supportedAlgorithms = supportedAlgorithms;
    }

    public UnsupportedAlgorithmException(String unsupportedAlgorithm, List<String> supportedAlgorithms, Throwable cause) {
        super(String.format("Algorithm '%s' is not supported. Supported algorithms: %s",
                unsupportedAlgorithm, supportedAlgorithms), cause);
        this.unsupportedAlgorithm = unsupportedAlgorithm;
        this.supportedAlgorithms = supportedAlgorithms;
    }

    public String getUnsupportedAlgorithm() {
        return unsupportedAlgorithm;
    }

    public List<String> getSupportedAlgorithms() {
        return supportedAlgorithms;
    }
}
