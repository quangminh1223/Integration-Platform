package msb.com.vn.crypto.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình keystore JKS cho crypto.
 *
 * <b>Chỉ lưu thông tin keystore</b> (đường dẫn + mật khẩu + alias private key của mình).
 * Public key của đối tác KHÔNG cấu hình ở đây — chúng nằm sẵn trong JKS và được
 * lấy theo alias lúc runtime qua {@link msb.com.vn.crypto.KeyStoreService#getPublicKey(String)}.
 *
 * Ví dụ application.yml:
 * <pre>
 * crypto:
 *   keystore:
 *     path: classpath:keystore/keys.jks   # hoặc file:/etc/secrets/keys.jks
 *     type: JKS
 *     store-password: ${CRYPTO_KEYSTORE_PASSWORD:}
 *     key-password: ${CRYPTO_KEY_PASSWORD:}
 *     key-alias: my-key                    # alias private key của mình
 * </pre>
 */
@ConfigurationProperties(prefix = "crypto.keystore")
public class CryptoKeyStoreProperties {

    /** Đường dẫn keystore — hỗ trợ classpath: / file: / đường dẫn tuyệt đối. */
    private String path;

    /** Loại keystore: JKS (mặc định) hoặc PKCS12. */
    private String type = "JKS";

    /** Mật khẩu mở keystore. */
    private String storePassword;

    /** Mật khẩu của entry private key (thường khác store password). */
    private String keyPassword;

    /** Alias private key của mình (để ký / giải mã). */
    private String keyAlias;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStorePassword() {
        return storePassword;
    }

    public void setStorePassword(String storePassword) {
        this.storePassword = storePassword;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public char[] storePasswordChars() {
        return storePassword != null ? storePassword.toCharArray() : new char[0];
    }

    public char[] keyPasswordChars() {
        return keyPassword != null ? keyPassword.toCharArray() : new char[0];
    }
}
