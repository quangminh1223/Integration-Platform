package msb.com.vn.crypto;

/**
 * Exception cho các lỗi mã hóa/giải mã/ký số.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
