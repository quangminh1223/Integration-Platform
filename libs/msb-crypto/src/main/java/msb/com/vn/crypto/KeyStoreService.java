package msb.com.vn.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Đọc key từ keystore (JKS / PKCS12) — framework-agnostic.
 *
 * Dùng cho mô hình: 1 keystore lưu CẢ
 *  - private key của mình (entry dạng PrivateKeyEntry, có password riêng)
 *  - public key / certificate của các đối tác (entry dạng TrustedCertificateEntry)
 *
 * <pre>
 *   // Load keystore 1 lần (vd lúc khởi động app)
 *   KeyStoreService ks = KeyStoreService.load(
 *           Path.of("config/keystore.jks"), "storePass".toCharArray(), "JKS");
 *
 *   // Private key của mình để KÝ / GIẢI MÃ
 *   PrivateKey myKey = ks.getPrivateKey("my-key", "keyPass".toCharArray());
 *   String signature = cryptoService.sign(data, myKey);
 *
 *   // Public key đối tác để VERIFY / MÃ HÓA
 *   PublicKey partnerKey = ks.getPublicKey("partner-a");
 *   boolean ok = cryptoService.verify(data, signature, partnerKey);
 * </pre>
 *
 * Lưu ý: KeyStore được giữ trong bộ nhớ sau khi load, nên việc lấy key là rẻ.
 */
public class KeyStoreService {

    /** Loại keystore mặc định. */
    public static final String TYPE_JKS = "JKS";
    public static final String TYPE_PKCS12 = "PKCS12";

    private final KeyStore keyStore;

    private KeyStoreService(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    // ─── Load ──────────────────────────────────────────────────────────────────

    /** Load keystore JKS từ file. */
    public static KeyStoreService load(Path keystorePath, char[] storePassword) {
        return load(keystorePath, storePassword, TYPE_JKS);
    }

    /** Load keystore từ file với type chỉ định (JKS / PKCS12). */
    public static KeyStoreService load(Path keystorePath, char[] storePassword, String type) {
        try (InputStream in = Files.newInputStream(keystorePath)) {
            return load(in, storePassword, type);
        } catch (IOException e) {
            throw new CryptoException("Không đọc được keystore: " + keystorePath + " — " + e.getMessage(), e);
        }
    }

    /**
     * Load keystore từ một {@link InputStream} (vd classpath resource).
     * Lưu ý: caller chịu trách nhiệm đóng stream gốc nếu cần; method này không đóng stream.
     */
    public static KeyStoreService load(InputStream in, char[] storePassword, String type) {
        try {
            KeyStore ks = KeyStore.getInstance(type != null ? type : TYPE_JKS);
            ks.load(in, storePassword);
            return new KeyStoreService(ks);
        } catch (Exception e) {
            throw new CryptoException("Không thể load keystore (" + type + "): " + e.getMessage(), e);
        }
    }

    /** Bọc một {@link KeyStore} đã load sẵn. */
    public static KeyStoreService of(KeyStore keyStore) {
        if (keyStore == null) {
            throw new CryptoException("KeyStore null");
        }
        return new KeyStoreService(keyStore);
    }

    // ─── Lấy key ─────────────────────────────────────────────────────────────────

    /** Lấy private key của mình theo alias + password của entry. */
    public PrivateKey getPrivateKey(String alias, char[] keyPassword) {
        try {
            if (!keyStore.containsAlias(alias)) {
                throw new CryptoException("Không tìm thấy alias '" + alias + "' trong keystore");
            }
            java.security.Key key = keyStore.getKey(alias, keyPassword);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new CryptoException("Alias '" + alias + "' không phải private key");
            }
            return privateKey;
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Không lấy được private key '" + alias + "': " + e.getMessage(), e);
        }
    }

    /** Lấy public key (từ certificate) theo alias — dùng cho key đối tác. */
    public PublicKey getPublicKey(String alias) {
        return getCertificate(alias).getPublicKey();
    }

    /** Lấy certificate theo alias. */
    public Certificate getCertificate(String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                throw new CryptoException("Không tìm thấy certificate cho alias '" + alias + "'");
            }
            return cert;
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Không lấy được certificate '" + alias + "': " + e.getMessage(), e);
        }
    }

    /** Lấy X.509 certificate theo alias (ném exception nếu không phải X.509). */
    public X509Certificate getX509Certificate(String alias) {
        Certificate cert = getCertificate(alias);
        if (!(cert instanceof X509Certificate x509)) {
            throw new CryptoException("Certificate của alias '" + alias + "' không phải X.509");
        }
        return x509;
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────────

    /** Danh sách toàn bộ alias trong keystore. */
    public List<String> aliases() {
        try {
            List<String> result = new ArrayList<>();
            Enumeration<String> e = keyStore.aliases();
            while (e.hasMoreElements()) {
                result.add(e.nextElement());
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            throw new CryptoException("Không liệt kê được alias: " + e.getMessage(), e);
        }
    }

    public boolean containsAlias(String alias) {
        try {
            return keyStore.containsAlias(alias);
        } catch (Exception e) {
            throw new CryptoException("Lỗi kiểm tra alias '" + alias + "': " + e.getMessage(), e);
        }
    }

    /** True nếu alias là entry chứa private key. */
    public boolean isKeyEntry(String alias) {
        try {
            return keyStore.isKeyEntry(alias);
        } catch (Exception e) {
            throw new CryptoException("Lỗi kiểm tra key entry '" + alias + "': " + e.getMessage(), e);
        }
    }

    /** Trả về {@link KeyStore} gốc nếu cần thao tác nâng cao. */
    public KeyStore getKeyStore() {
        return keyStore;
    }
}
