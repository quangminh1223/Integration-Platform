package msb.com.vn.qrservice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception cho các lỗi xử lý bản tin ISO8583
 */
public class Iso8583Exception extends QrException {

    public Iso8583Exception(String message) {
        super(message, HttpStatus.BAD_REQUEST, "ISO8583_ERROR");
    }

    public Iso8583Exception(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "ISO8583_ERROR");
    }

    public Iso8583Exception(String message, String errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}
