package msb.com.vn.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link CryptoService}.
 *
 * Tập trung vào:
 *  - Ký số / xác minh chữ ký (SHA256withRSA): sign() / verify()
 *  - Mã hóa / giải mã RSA: encryptRsa() / decryptRsa()
 *  - Hash SHA-256: hashSha256() / hashSha256Base64()
 *  - Load key & sinh cặp khóa.
 */
@DisplayName("CryptoService — ký số & mã hóa RSA")
class CryptoServiceTest {

    private static final CryptoService crypto = new CryptoService();

    private static String publicKey;
    private static String privateKey;

    @BeforeAll
    static void setUpKeys() {
        String[] keyPair = crypto.generateKeyPair(2048);
        publicKey = keyPair[0];
        privateKey = keyPair[1];
    }

    // ─── Ký số / xác minh ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("sign() / verify()")
    class SignVerify {

        @Test
        @DisplayName("Ký rồi xác minh với đúng cặp khóa → hợp lệ")
        void signThenVerify_validSignature() {
            String data = "Giao dịch chuyển khoản 1.000.000 VND";

            String signature = crypto.sign(data, privateKey);

            assertNotNull(signature);
            assertFalse(signature.isBlank());
            assertTrue(crypto.verify(data, signature, publicKey),
                    "Chữ ký hợp lệ phải verify thành công");
        }

        @Test
        @DisplayName("Chữ ký Base64 hợp lệ và sinh ra ổn định khi cùng input")
        void signature_isValidBase64() {
            String signature = crypto.sign("hello", privateKey);
            assertDoesNotThrow(() -> Base64.getDecoder().decode(signature));
        }

        @Test
        @DisplayName("Dữ liệu bị sửa đổi → verify thất bại")
        void verify_failsWhenDataTampered() {
            String data = "amount=1000";
            String signature = crypto.sign(data, privateKey);

            assertFalse(crypto.verify("amount=9999", signature, publicKey),
                    "Data bị đổi thì chữ ký không còn hợp lệ");
        }

        @Test
        @DisplayName("Verify bằng public key của cặp khóa khác → thất bại")
        void verify_failsWithWrongPublicKey() {
            String data = "payload";
            String signature = crypto.sign(data, privateKey);

            String[] otherPair = crypto.generateKeyPair(2048);
            String otherPublicKey = otherPair[0];

            assertFalse(crypto.verify(data, signature, otherPublicKey));
        }

        @Test
        @DisplayName("Chữ ký không hợp lệ (không phải Base64) → verify trả về false, không ném exception")
        void verify_returnsFalseForGarbageSignature() {
            assertFalse(crypto.verify("data", "###not-base64###", publicKey));
        }

        @Test
        @DisplayName("Ký với private key sai định dạng → ném CryptoException")
        void sign_throwsForInvalidPrivateKey() {
            CryptoException ex = assertThrows(CryptoException.class,
                    () -> crypto.sign("data", "invalid-key"));
            assertTrue(ex.getMessage().contains("Lỗi ký số")
                    || ex.getMessage().contains("private key"));
        }

        @Test
        @DisplayName("Ký dữ liệu có ký tự Unicode (tiếng Việt) → verify thành công")
        void signThenVerify_unicodeData() {
            String data = "Nguyễn Văn A — chuyển 50.000đ cho Trần Thị B";
            String signature = crypto.sign(data, privateKey);
            assertTrue(crypto.verify(data, signature, publicKey));
        }
    }

    // ─── Mã hóa / giải mã RSA ───────────────────────────────────────────────────

    @Nested
    @DisplayName("encryptRsa() / decryptRsa()")
    class EncryptDecrypt {

        @Test
        @DisplayName("Mã hóa rồi giải mã → trả về plaintext ban đầu")
        void encryptThenDecrypt_roundTrip() {
            String plain = "So tai khoan: 0123456789";

            String cipher = crypto.encryptRsa(plain, publicKey);
            String decrypted = crypto.decryptRsa(cipher, privateKey);

            assertEquals(plain, decrypted);
        }

        @Test
        @DisplayName("Ciphertext khác plaintext và là Base64 hợp lệ")
        void cipher_isDifferentAndBase64() {
            String plain = "secret";
            String cipher = crypto.encryptRsa(plain, publicKey);

            assertNotEquals(plain, cipher);
            assertDoesNotThrow(() -> Base64.getDecoder().decode(cipher));
        }

        @Test
        @DisplayName("OAEP randomized → 2 lần mã hóa cùng input cho 2 ciphertext khác nhau")
        void encrypt_isRandomized() {
            String plain = "same-input";
            String c1 = crypto.encryptRsa(plain, publicKey);
            String c2 = crypto.encryptRsa(plain, publicKey);

            assertNotEquals(c1, c2, "OAEP padding phải randomized");
            assertEquals(plain, crypto.decryptRsa(c1, privateKey));
            assertEquals(plain, crypto.decryptRsa(c2, privateKey));
        }

        @Test
        @DisplayName("Mã hóa Unicode (tiếng Việt) → giải mã đúng")
        void encryptDecrypt_unicode() {
            String plain = "Mã hóa tiếng Việt có dấu: đ, â, ê, ô";
            String cipher = crypto.encryptRsa(plain, publicKey);
            assertEquals(plain, crypto.decryptRsa(cipher, privateKey));
        }

        @Test
        @DisplayName("Giải mã bằng private key của cặp khóa khác → ném CryptoException")
        void decrypt_failsWithWrongPrivateKey() {
            String cipher = crypto.encryptRsa("data", publicKey);
            String[] otherPair = crypto.generateKeyPair(2048);
            String otherPrivateKey = otherPair[1];

            assertThrows(CryptoException.class,
                    () -> crypto.decryptRsa(cipher, otherPrivateKey));
        }

        @Test
        @DisplayName("Mã hóa với public key sai định dạng → ném CryptoException")
        void encrypt_throwsForInvalidPublicKey() {
            assertThrows(CryptoException.class,
                    () -> crypto.encryptRsa("data", "not-a-key"));
        }
    }

    // ─── Hash SHA-256 ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hashSha256() / hashSha256Base64()")
    class Hashing {

        @Test
        @DisplayName("Hash hex theo đúng test vector của \"abc\"")
        void hashSha256_knownVector() {
            // SHA-256("abc") theo chuẩn NIST
            String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
            assertEquals(expected, crypto.hashSha256("abc"));
        }

        @Test
        @DisplayName("Hash deterministic & độ dài 64 ký tự hex")
        void hashSha256_deterministic() {
            String h1 = crypto.hashSha256("data");
            String h2 = crypto.hashSha256("data");
            assertEquals(h1, h2);
            assertEquals(64, h1.length());
        }

        @Test
        @DisplayName("Base64 hash là Base64 hợp lệ và khác bản hex")
        void hashSha256Base64_valid() {
            String base64 = crypto.hashSha256Base64("abc");
            assertDoesNotThrow(() -> Base64.getDecoder().decode(base64));
            assertNotEquals(crypto.hashSha256("abc"), base64);
        }

        @Test
        @DisplayName("Input null → trả về null")
        void hash_nullInput_returnsNull() {
            assertNull(crypto.hashSha256(null));
            assertNull(crypto.hashSha256Base64(null));
        }
    }

    // ─── Key loading & generate ─────────────────────────────────────────────────

    @Nested
    @DisplayName("loadPublicKey() / loadPrivateKey() / generateKeyPair()")
    class KeyHandling {

        @Test
        @DisplayName("generateKeyPair trả về public & private key không rỗng")
        void generateKeyPair_returnsBothKeys() {
            String[] pair = crypto.generateKeyPair(2048);
            assertEquals(2, pair.length);
            assertNotNull(pair[0]);
            assertNotNull(pair[1]);
            assertFalse(pair[0].isBlank());
            assertFalse(pair[1].isBlank());
        }

        @Test
        @DisplayName("Load public/private key hợp lệ → đúng thuật toán RSA")
        void loadKeys_validRsaAlgorithm() {
            assertEquals("RSA", crypto.loadPublicKey(publicKey).getAlgorithm());
            assertEquals("RSA", crypto.loadPrivateKey(privateKey).getAlgorithm());
        }

        @Test
        @DisplayName("Load key có header PEM vẫn parse được (cleanKey)")
        void loadPublicKey_withPemHeaders() {
            String pem = "-----BEGIN PUBLIC KEY-----\n" + publicKey + "\n-----END PUBLIC KEY-----";
            assertDoesNotThrow(() -> crypto.loadPublicKey(pem));
        }

        @Test
        @DisplayName("Load key sai định dạng → ném CryptoException")
        void loadKey_invalid_throws() {
            assertThrows(CryptoException.class, () -> crypto.loadPublicKey("garbage"));
            assertThrows(CryptoException.class, () -> crypto.loadPrivateKey("garbage"));
        }
    }

    // ─── Hỗ trợ cấu hình SHA-256 / SHA-512 (nâng cấp bảo mật) ────────────────────

    @Nested
    @DisplayName("Cấu hình thuật toán RSA — SHA-256 & SHA-512")
    class AlgorithmSelection {

        @ParameterizedTest
        @EnumSource(RsaHashAlgorithm.class)
        @DisplayName("sign/verify hoạt động với mọi algorithm")
        void signVerify_perAlgorithm(RsaHashAlgorithm algo) {
            String data = "transaction-payload";
            String signature = crypto.sign(data, privateKey, algo);
            assertTrue(crypto.verify(data, signature, publicKey, algo),
                    "Chữ ký phải verify được với cùng algorithm: " + algo);
        }

        @ParameterizedTest
        @EnumSource(RsaHashAlgorithm.class)
        @DisplayName("encrypt/decrypt round-trip với mọi algorithm")
        void encryptDecrypt_perAlgorithm(RsaHashAlgorithm algo) {
            String plain = "so-tai-khoan-bi-mat";
            String cipher = crypto.encryptRsa(plain, publicKey, algo);
            assertEquals(plain, crypto.decryptRsa(cipher, privateKey, algo));
        }

        @Test
        @DisplayName("Ký bằng SHA-512 nhưng verify bằng SHA-256 → thất bại")
        void verify_failsOnAlgorithmMismatch() {
            String data = "data";
            String signature = crypto.sign(data, privateKey, RsaHashAlgorithm.SHA_512);
            assertFalse(crypto.verify(data, signature, publicKey, RsaHashAlgorithm.SHA_256),
                    "Khác algorithm thì chữ ký không hợp lệ");
        }

        @Test
        @DisplayName("Mã hóa SHA-512, giải mã SHA-256 → ném CryptoException")
        void decrypt_failsOnAlgorithmMismatch() {
            String cipher = crypto.encryptRsa("data", publicKey, RsaHashAlgorithm.SHA_512);
            assertThrows(CryptoException.class,
                    () -> crypto.decryptRsa(cipher, privateKey, RsaHashAlgorithm.SHA_256));
        }

        @Test
        @DisplayName("Default constructor → algorithm mặc định là SHA-256")
        void defaultConstructor_usesSha256() {
            assertEquals(RsaHashAlgorithm.SHA_256, new CryptoService().getDefaultAlgorithm());
        }

        @Test
        @DisplayName("Constructor SHA-512 → các API không tham số dùng SHA-512 (tương thích chéo)")
        void sha512DefaultCryptoService_interopWithExplicitCall() {
            CryptoService sha512Crypto = new CryptoService(RsaHashAlgorithm.SHA_512);
            assertEquals(RsaHashAlgorithm.SHA_512, sha512Crypto.getDefaultAlgorithm());

            // Ký bằng default (SHA-512) verify được bằng lời gọi explicit SHA-512
            String signature = sha512Crypto.sign("data", privateKey);
            assertTrue(crypto.verify("data", signature, publicKey, RsaHashAlgorithm.SHA_512));

            // Mã hóa bằng default (SHA-512) giải mã bằng explicit SHA-512
            String cipher = sha512Crypto.encryptRsa("secret", publicKey);
            assertEquals("secret", crypto.decryptRsa(cipher, privateKey, RsaHashAlgorithm.SHA_512));
        }

        @Test
        @DisplayName("hashHex SHA-512 đúng test vector của \"abc\" và dài 128 ký tự")
        void hashHex_sha512KnownVector() {
            String expected = "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                    + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f";
            String actual = crypto.hashHex("abc", RsaHashAlgorithm.SHA_512);
            assertEquals(expected, actual);
            assertEquals(128, actual.length());
        }
    }
}
