package msb.com.vn.qrservice.common.exception;

import org.springframework.http.HttpStatus;

public class QrParseException extends QrException {

    public QrParseException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "QR_PARSE_ERROR");
    }

    public QrParseException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "QR_PARSE_ERROR");
    }
}
