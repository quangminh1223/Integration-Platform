package msb.com.vn.crypto;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Core crypto — framework-agnostic (KHÔNG phụ thuộc Spring).
 * Dùng được ở mọi project: Spring Boot, Quarkus, plain Java.
 *
 * Cung cấp:
 *  1. SHA hashing               : hashSha256() / hash(.., algo)
 *  2. RSA encrypt/decrypt        : encryptRsa() / decryptRsa()
 *  3. RSA sign/verify            : sign() / verify()
 *
 * <b>Nâng cấp bảo mật:</b>
 * Toàn bộ thao tác RSA & hash đều cấu hình được qua {@link RsaHashAlgorithm}
 * (hiện hỗ trợ SHA-256 và SHA-512). Mặc định là {@link RsaHashAlgorithm#SHA_256}
 * để tương thích ngược. Khi cần tăng cường:
 * <pre>
 *   CryptoService crypto = new CryptoService(RsaHashAlgorithm.SHA_512);
 *   // hoặc chỉ định theo từng lời gọi:
 *   crypto.sign(data, privateKey, RsaHashAlgorithm.SHA_512);
 * </pre>
 *
 * <b>Lưu ý:</b> khi verify/decrypt phải dùng ĐÚNG algorithm đã ký/mã hóa.
 *
 * <b>Lưu ý về RSA:</b>
 * - RSA chỉ mã hóa được data NHỎ HƠN key size (2048-bit → ~245 bytes với OAEP).
 * - Với payload lớn, dùng {@link HybridCryptoService} (AES-128-GCM + RSA).
 *
 * Quy ước key:
 * - Public key  : X.509 (SubjectPublicKeyInfo), Base64
 * - Private key : PKCS#8, Base64
 */
public class CryptoService {

    private static final String RSA = "RSA";

    /** Algorithm mặc định khi gọi các API không chỉ định cụ thể. */
    private final RsaHashAlgorithm defaultAlgorithm;

    /** Khởi tạo với mặc định SHA-256 (tương thích ngược). */
    public CryptoService() {
        this(RsaHashAlgorithm.SHA_256);
    }

    /** Khởi tạo với algorithm mặc định tùy chọn (vd: SHA-512). */
    public CryptoService(RsaHashAlgorithm defaultAlgorithm) {
        this.defaultAlgorithm = (defaultAlgorithm != null) ? defaultAlgorithm : RsaHashAlgorithm.SHA_256;
    }

    public RsaHashAlgorithm getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    // ─── Hashing ──────────────────────────────────────────────────────────────

    public String hashSha256(String data) {
        return hashHex(data, RsaHashAlgorithm.SHA_256);
    }

    public String hashSha256Base64(String data) {
        return hashBase64(data, RsaHashAlgorithm.SHA_256);
    }

    /** Hash theo algorithm chỉ định, trả về chuỗi HEX. */
    public String hashHex(String data, RsaHashAlgorithm algorithm) {
        if (data == null) return null;
        return HexFormat.of().formatHex(digest(data, algorithm));
    }

    /** Hash theo algorithm chỉ định, trả về chuỗi Base64. */
    public String hashBase64(String data, RsaHashAlgorithm algorithm) {
        if (data == null) return null;
        return Base64.getEncoder().encodeToString(digest(data, algorithm));
    }

    private byte[] digest(String data, RsaHashAlgorithm algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.digest());
            return digest.digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("Không thể hash " + algorithm.digest() + ": " + e.getMessage(), e);
        }
    }

    // ─── RSA encrypt / decrypt ────────────────────────────────────────────────

    public String encryptRsa(String plainText, String publicKeyBase64) {
        return encryptRsa(plainText, publicKeyBase64, defaultAlgorithm);
    }

    public String encryptRsa(String plainText, String publicKeyBase64, RsaHashAlgorithm algorithm) {
        return encryptRsa(plainText, loadPublicKey(publicKeyBase64), algorithm);
    }

    /** Mã hóa với {@link PublicKey} có sẵn (vd lấy từ keystore JKS), dùng algorithm mặc định. */
    public String encryptRsa(String plainText, PublicKey publicKey) {
        return encryptRsa(plainText, publicKey, defaultAlgorithm);
    }

    public String encryptRsa(String plainText, PublicKey publicKey, RsaHashAlgorithm algorithm) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm.rsaTransform());
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new CryptoException("Lỗi mã hóa RSA: " + e.getMessage(), e);
        }
    }

    public String decryptRsa(String cipherTextBase64, String privateKeyBase64) {
        return decryptRsa(cipherTextBase64, privateKeyBase64, defaultAlgorithm);
    }

    public String decryptRsa(String cipherTextBase64, String privateKeyBase64, RsaHashAlgorithm algorithm) {
        return decryptRsa(cipherTextBase64, loadPrivateKey(privateKeyBase64), algorithm);
    }

    /** Giải mã với {@link PrivateKey} có sẵn (vd lấy từ keystore JKS), dùng algorithm mặc định. */
    public String decryptRsa(String cipherTextBase64, PrivateKey privateKey) {
        return decryptRsa(cipherTextBase64, privateKey, defaultAlgorithm);
    }

    public String decryptRsa(String cipherTextBase64, PrivateKey privateKey, RsaHashAlgorithm algorithm) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm.rsaTransform());
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decoded = Base64.getDecoder().decode(cipherTextBase64);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("Lỗi giải mã RSA: " + e.getMessage(), e);
        }
    }

    // ─── RSA sign / verify ──────────────────────────────────────────────────────

    public String sign(String data, String privateKeyBase64) {
        return sign(data, privateKeyBase64, defaultAlgorithm);
    }

    public String sign(String data, String privateKeyBase64, RsaHashAlgorithm algorithm) {
        return sign(data, loadPrivateKey(privateKeyBase64), algorithm);
    }

    /** Ký với {@link PrivateKey} có sẵn (vd lấy từ keystore JKS), dùng algorithm mặc định. */
    public String sign(String data, PrivateKey privateKey) {
        return sign(data, privateKey, defaultAlgorithm);
    }

    public String sign(String data, PrivateKey privateKey, RsaHashAlgorithm algorithm) {
        try {
            Signature signature = Signature.getInstance(algorithm.signatureAlgorithm());
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            throw new CryptoException("Lỗi ký số: " + e.getMessage(), e);
        }
    }

    public boolean verify(String data, String signatureBase64, String publicKeyBase64) {
        return verify(data, signatureBase64, publicKeyBase64, defaultAlgorithm);
    }

    public boolean verify(String data, String signatureBase64, String publicKeyBase64, RsaHashAlgorithm algorithm) {
        try {
            return verify(data, signatureBase64, loadPublicKey(publicKeyBase64), algorithm);
        } catch (CryptoException e) {
            return false;
        }
    }

    /** Verify với {@link PublicKey} có sẵn (vd lấy từ keystore JKS), dùng algorithm mặc định. */
    public boolean verify(String data, String signatureBase64, PublicKey publicKey) {
        return verify(data, signatureBase64, publicKey, defaultAlgorithm);
    }

    public boolean verify(String data, String signatureBase64, PublicKey publicKey, RsaHashAlgorithm algorithm) {
        try {
            Signature signature = Signature.getInstance(algorithm.signatureAlgorithm());
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] sig = Base64.getDecoder().decode(signatureBase64);
            return signature.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Key loading ──────────────────────────────────────────────────────────

    public PublicKey loadPublicKey(String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(cleanKey(publicKeyBase64));
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new CryptoException("Không thể load public key: " + e.getMessage(), e);
        }
    }

    public PrivateKey loadPrivateKey(String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(cleanKey(privateKeyBase64));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new CryptoException("Không thể load private key: " + e.getMessage(), e);
        }
    }

    public String[] generateKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(keySize);
            KeyPair keyPair = generator.generateKeyPair();
            String pub = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String priv = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return new String[]{pub, priv};
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("Không thể tạo key pair: " + e.getMessage(), e);
        }
    }

    private String cleanKey(String key) {
        return key
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s+", "");
    }
}
