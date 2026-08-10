package msb.com.vn.qrservice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Phân loại lỗi khi gọi backend — giúp luồng xử lý react đúng cách.
 *
 * - TIMEOUT     : backend không phản hồi kịp → có thể retry
 * - CONNECTION  : không kết nối được backend → có thể retry / circuit break
 * - BUSINESS    : backend trả lỗi nghiệp vụ (4xx) → KHÔNG retry, trả lỗi cho client
 * - SYSTEM      : backend lỗi hệ thống (5xx) → có thể retry
 * - SERIALIZATION: lỗi parse request/response → KHÔNG retry (lỗi code/data)
 * - UNKNOWN     : lỗi không xác định
 */
public enum BackendErrorType {

    TIMEOUT("BE_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, true),
    CONNECTION("BE_CONNECTION", HttpStatus.SERVICE_UNAVAILABLE, true),
    BUSINESS("BE_BUSINESS", HttpStatus.BAD_REQUEST, false),
    SYSTEM("BE_SYSTEM", HttpStatus.BAD_GATEWAY, true),
    SERIALIZATION("BE_SERIALIZATION", HttpStatus.INTERNAL_SERVER_ERROR, false),
    UNKNOWN("BE_UNKNOWN", HttpStatus.INTERNAL_SERVER_ERROR, false);

    private final String code;
    private final HttpStatus httpStatus;
    private final boolean retryable;

    BackendErrorType(String code, HttpStatus httpStatus, boolean retryable) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
