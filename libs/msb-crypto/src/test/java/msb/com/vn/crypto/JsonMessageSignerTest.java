package msb.com.vn.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test ký/verify node "body" của bản tin transfer bằng SHA256withRSA.
 */
@DisplayName("JsonMessageSigner — ký & verify body (SHA-256)")
class JsonMessageSignerTest {

    private static final CryptoService crypto = new CryptoService();
    private static final JsonMessageSigner signer = new JsonMessageSigner(crypto);

    private static String publicKey;
    private static String privateKey;

    private static final String MESSAGE = """
            {"transferFromCASAToCASA": {
              "authenInfo": {"req_id": "IBS20231218154748-4c4fa15008f4-2","srv": "SRV930","req_time": "20231218154748","req_app": "PAY","authorizer": "PAYUSER","password": "6EFn5BLimI85zfyYAmxhDxuLOvlF5i"},
              "commonInfo": {"channel": "SML","branchCode": "VN0011000","hostName": "SML","teller": "IBSML247","manager": "EBANKING02","tranSeq": "8078383","tranDate": "181223","tranCode": "NP1321I"},
              "body": {"creditAccount": "80000002233","creditAmount": "98000","creditCurrency": "VND","creditRate": "10000000","debitAccount": "VND1217000011000","debitAmount": "98000","debitCurrency": "VND","debitRate": "10000000","description": "-704869-test timeout esb ok","vatFee": "0","serviceFee": "0","feeOwn": ""}
            }}
            """;

    @BeforeAll
    static void setUp() {
        String[] pair = crypto.generateKeyPair(2048);
        publicKey = pair[0];
        privateKey = pair[1];
    }

    @Test
    @DisplayName("Ký body rồi verify → hợp lệ")
    void signBody_thenVerify() {
        String signature = signer.signBody(MESSAGE, privateKey);

        assertNotNull(signature);
        assertFalse(signature.isBlank());
        assertTrue(signer.verifyBody(MESSAGE, signature, publicKey));
    }

    @Test
    @DisplayName("Bản tin format khác (đảo field, thêm khoảng trắng) → chữ ký vẫn khớp")
    void verify_isStableAcrossFormatting() {
        String signature = signer.signBody(MESSAGE, privateKey);

        // Cùng nội dung body nhưng đảo thứ tự field và đổi khoảng trắng
        String reordered = """
                {
                  "transferFromCASAToCASA": {
                    "body": {
                        "feeOwn": "",
                        "serviceFee": "0",
                        "vatFee": "0",
                        "description": "-704869-test timeout esb ok",
                        "debitRate": "10000000",
                        "debitCurrency": "VND",
                        "debitAmount": "98000",
                        "debitAccount": "VND1217000011000",
                        "creditRate": "10000000",
                        "creditCurrency": "VND",
                        "creditAmount": "98000",
                        "creditAccount": "80000002233"
                    },
                    "commonInfo": {"tranCode": "NP1321I"}
                  }
                }
                """;

        assertTrue(signer.verifyBody(reordered, signature, publicKey),
                "Canonical hóa phải giúp chữ ký ổn định bất kể thứ tự field / khoảng trắng");
    }

    @Test
    @DisplayName("Body bị thay đổi (đổi số tiền) → verify thất bại")
    void verify_failsWhenBodyTampered() {
        String signature = signer.signBody(MESSAGE, privateKey);

        String tampered = MESSAGE.replace("\"creditAmount\": \"98000\"", "\"creditAmount\": \"990000\"");

        assertFalse(signer.verifyBody(tampered, signature, publicKey));
    }

    @Test
    @DisplayName("canonicalContent là JSON compact, field đã sort alphabet")
    void canonicalContent_sortedAndCompact() {
        String canonical = signer.canonicalContent(MESSAGE, "body");

        assertFalse(canonical.contains(" "), "Không được chứa khoảng trắng");
        assertFalse(canonical.contains("\n"));
        // field đầu tiên sau '{' phải là creditAccount (sort alphabet)
        assertTrue(canonical.startsWith("{\"creditAccount\":"));
    }

    @Test
    @DisplayName("Bản tin không có field body → ném CryptoException")
    void signBody_throwsWhenNoBody() {
        String noBody = "{\"transferFromCASAToCASA\":{\"commonInfo\":{\"tranCode\":\"NP1321I\"}}}";
        CryptoException ex = assertThrows(CryptoException.class,
                () -> signer.signBody(noBody, privateKey));
        assertTrue(ex.getMessage().contains("body"));
    }

    @Test
    @DisplayName("Verify thất bại với public key của cặp khóa khác")
    void verify_failsWithWrongKey() {
        String signature = signer.signBody(MESSAGE, privateKey);
        String otherPublicKey = crypto.generateKeyPair(2048)[0];

        assertFalse(signer.verifyBody(MESSAGE, signature, otherPublicKey));
    }
}
