package msb.com.vn.qrservice.common.exception;

/**
 * Exception cho lỗi gọi backend, mang theo phân loại lỗi (BackendErrorType).
 *
 * Dùng để luồng xử lý phía trên biết được loại lỗi và react đúng:
 * - retryable → có thể retry
 * - business → trả lỗi cho client ngay
 */
public class BackendCallException extends RuntimeException {

    private final BackendErrorType errorType;
    private final String operationId;
    private final String targetUrl;

    public BackendCallException(BackendErrorType errorType, String operationId,
                                 String targetUrl, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.operationId = operationId;
        this.targetUrl = targetUrl;
    }

    public BackendCallException(BackendErrorType errorType, String operationId,
                                 String targetUrl, String message) {
        this(errorType, operationId, targetUrl, message, null);
    }

    public BackendErrorType getErrorType() {
        return errorType;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public boolean isRetryable() {
        return errorType.isRetryable();
    }
}
