package msb.com.vn.qrservice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception cho QR Service
 */
public class QrException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public QrException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public QrException(String message, Throwable cause, HttpStatus httpStatus, String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
