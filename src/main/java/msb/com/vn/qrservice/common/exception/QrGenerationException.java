package msb.com.vn.qrservice.common.exception;

import org.springframework.http.HttpStatus;

public class QrGenerationException extends QrException {

    public QrGenerationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "QR_GENERATION_ERROR");
    }

    public QrGenerationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "QR_GENERATION_ERROR");
    }
}
