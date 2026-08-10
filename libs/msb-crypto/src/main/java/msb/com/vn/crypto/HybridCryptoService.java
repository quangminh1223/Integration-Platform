package msb.com.vn.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mã hóa HYBRID cho payload lớn (RSA + AES-128-GCM). Framework-agnostic.
 *
 * - AES-128-GCM mã hóa payload (nhanh, không giới hạn size, có auth tag)
 * - RSA-OAEP-SHA256 mã hóa AES key
 * - Gói {encryptedKey, iv, cipherText} → JSON Base64 envelope
 */
public class HybridCryptoService {

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String AES = "AES";
    private static final String AES_TRANSFORM = "AES/GCM/NoPadding";
    private static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int AES_KEY_SIZE = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    public HybridCryptoService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /** Constructor tiện lợi — tự tạo CryptoService nội bộ. */
    public HybridCryptoService() {
        this(new CryptoService());
    }

    // ─── Encrypt ──────────────────────────────────────────────────────────────

    public String encrypt(String plainText, String publicKeyBase64) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES);
            keyGen.init(AES_KEY_SIZE);
            SecretKey aesKey = keyGen.generateKey();

            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORM);
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = aesCipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            PublicKey publicKey = cryptoService.loadPublicKey(publicKeyBase64);
            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORM);
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

            CryptoEnvelope envelope = new CryptoEnvelope(
                    "RSA-OAEP-SHA256+AES-128-GCM",
                    Base64.getEncoder().encodeToString(encryptedAesKey),
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(cipherText));

            String json = objectMapper.writeValueAsString(envelope);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CryptoException("Lỗi mã hóa hybrid: " + e.getMessage(), e);
        }
    }

    // ─── Decrypt ──────────────────────────────────────────────────────────────

    public String decrypt(String envelopeBase64, String privateKeyBase64) {
        try {
            String json = new String(Base64.getDecoder().decode(envelopeBase64), StandardCharsets.UTF_8);
            CryptoEnvelope envelope = objectMapper.readValue(json, CryptoEnvelope.class);

            PrivateKey privateKey = cryptoService.loadPrivateKey(privateKeyBase64);
            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORM);
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] aesKeyBytes = rsaCipher.doFinal(Base64.getDecoder().decode(envelope.getEncryptedKey()));
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, AES);

            byte[] iv = Base64.getDecoder().decode(envelope.getIv());
            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORM);
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = aesCipher.doFinal(Base64.getDecoder().decode(envelope.getCipherText()));

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("Lỗi giải mã hybrid: " + e.getMessage(), e);
        }
    }

    // ─── Encrypt + Sign ───────────────────────────────────────────────────────

    public String[] encryptAndSign(String plainText, String receiverPublicKey, String senderPrivateKey) {
        String envelope = encrypt(plainText, receiverPublicKey);
        String signature = cryptoService.sign(envelope, senderPrivateKey);
        return new String[]{envelope, signature};
    }

    public String verifyAndDecrypt(String envelope, String signature,
                                   String senderPublicKey, String receiverPrivateKey) {
        if (!cryptoService.verify(envelope, signature, senderPublicKey)) {
            throw new CryptoException("Chữ ký không hợp lệ — payload có thể đã bị thay đổi");
        }
        return decrypt(envelope, receiverPrivateKey);
    }

    // ─── Envelope model (POJO thuần, không Lombok để lib gọn) ────────────────

    public static class CryptoEnvelope {
        private String alg;
        private String encryptedKey;
        private String iv;
        private String cipherText;

        public CryptoEnvelope() {}

        public CryptoEnvelope(String alg, String encryptedKey, String iv, String cipherText) {
            this.alg = alg;
            this.encryptedKey = encryptedKey;
            this.iv = iv;
            this.cipherText = cipherText;
        }

        public String getAlg() { return alg; }
        public void setAlg(String alg) { this.alg = alg; }
        public String getEncryptedKey() { return encryptedKey; }
        public void setEncryptedKey(String encryptedKey) { this.encryptedKey = encryptedKey; }
        public String getIv() { return iv; }
        public void setIv(String iv) { this.iv = iv; }
        public String getCipherText() { return cipherText; }
        public void setCipherText(String cipherText) { this.cipherText = cipherText; }
    }
}
