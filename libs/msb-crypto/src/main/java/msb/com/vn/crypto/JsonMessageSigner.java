package msb.com.vn.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ký số / xác minh chữ ký cho một phần (node) của bản tin JSON — mặc định là node "body".
 *
 * <b>Vì sao cần canonical hóa?</b>
 * Hai bản tin JSON cùng nội dung nhưng khác thứ tự field hoặc khác khoảng trắng
 * sẽ cho ra chuỗi byte khác nhau → chữ ký không khớp. Trước khi ký, lớp này
 * "canonical hóa" node:
 *  - Sắp xếp tên field theo thứ tự alphabet (đệ quy)
 *  - Loại bỏ toàn bộ khoảng trắng / xuống dòng (Jackson compact)
 *  - Giữ nguyên escaping chuẩn JSON
 *
 * Nhờ đó bên ký và bên verify luôn làm việc trên cùng một chuỗi byte, bất kể
 * bản tin được format thế nào.
 *
 * Thuật toán ký: SHA256withRSA (qua {@link CryptoService}).
 *
 * <pre>
 *   JsonMessageSigner signer = new JsonMessageSigner();
 *   String signature = signer.signBody(message, privateKey);
 *   boolean ok       = signer.verifyBody(message, signature, publicKey);
 * </pre>
 */
public class JsonMessageSigner {

    private static final String DEFAULT_FIELD = "body";

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final RsaHashAlgorithm algorithm;

    public JsonMessageSigner() {
        this(new CryptoService(), new ObjectMapper(), RsaHashAlgorithm.SHA_256);
    }

    public JsonMessageSigner(CryptoService cryptoService) {
        this(cryptoService, new ObjectMapper(), RsaHashAlgorithm.SHA_256);
    }

    public JsonMessageSigner(CryptoService cryptoService, ObjectMapper objectMapper, RsaHashAlgorithm algorithm) {
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
        this.algorithm = (algorithm != null) ? algorithm : RsaHashAlgorithm.SHA_256;
    }

    // ─── API tiện dụng: ký/verify node "body" ────────────────────────────────

    /** Ký node "body" của bản tin, trả về chữ ký Base64 (SHA256withRSA). */
    public String signBody(String message, String privateKeyBase64) {
        return signField(message, DEFAULT_FIELD, privateKeyBase64);
    }

    /** Xác minh chữ ký của node "body". */
    public boolean verifyBody(String message, String signatureBase64, String publicKeyBase64) {
        return verifyField(message, DEFAULT_FIELD, signatureBase64, publicKeyBase64);
    }

    // ─── API tổng quát: ký/verify theo tên field bất kỳ ──────────────────────

    /** Ký node có tên {@code fieldName} (tìm đệ quy trong bản tin). */
    public String signField(String message, String fieldName, String privateKeyBase64) {
        String canonical = canonicalContent(message, fieldName);
        return cryptoService.sign(canonical, privateKeyBase64, algorithm);
    }

    /** Xác minh chữ ký node có tên {@code fieldName}. */
    public boolean verifyField(String message, String fieldName, String signatureBase64, String publicKeyBase64) {
        try {
            String canonical = canonicalContent(message, fieldName);
            return cryptoService.verify(canonical, signatureBase64, publicKeyBase64, algorithm);
        } catch (CryptoException e) {
            return false;
        }
    }

    // ─── API dùng Key object (vd lấy từ keystore JKS) ─────────────────────────

    /** Ký node "body" bằng {@link PrivateKey} của mình (lấy từ JKS). */
    public String signBody(String message, PrivateKey privateKey) {
        return signField(message, DEFAULT_FIELD, privateKey);
    }

    /** Xác minh chữ ký node "body" bằng {@link PublicKey} đối tác (lấy từ JKS). */
    public boolean verifyBody(String message, String signatureBase64, PublicKey publicKey) {
        return verifyField(message, DEFAULT_FIELD, signatureBase64, publicKey);
    }

    /** Ký node {@code fieldName} bằng {@link PrivateKey}. */
    public String signField(String message, String fieldName, PrivateKey privateKey) {
        String canonical = canonicalContent(message, fieldName);
        return cryptoService.sign(canonical, privateKey, algorithm);
    }

    /** Xác minh chữ ký node {@code fieldName} bằng {@link PublicKey}. */
    public boolean verifyField(String message, String fieldName, String signatureBase64, PublicKey publicKey) {
        try {
            String canonical = canonicalContent(message, fieldName);
            return cryptoService.verify(canonical, signatureBase64, publicKey, algorithm);
        } catch (CryptoException e) {
            return false;
        }
    }

    /**
     * Trả về chuỗi canonical (đã sort field + compact) của node — hữu ích để
     * log/đối soát hoặc tự tính hash. Không ký.
     */
    public String canonicalContent(String message, String fieldName) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode target = findField(root, fieldName);
            if (target == null || target.isMissingNode()) {
                throw new CryptoException("Không tìm thấy field '" + fieldName + "' trong bản tin");
            }
            return objectMapper.writeValueAsString(sortNode(target));
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Bản tin JSON không hợp lệ: " + e.getMessage(), e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Tìm đệ quy field đầu tiên có tên {@code fieldName} (BFS theo cấu trúc). */
    private JsonNode findField(JsonNode node, String fieldName) {
        if (node == null) return null;
        if (node.isObject()) {
            JsonNode direct = node.get(fieldName);
            if (direct != null) return direct;
            for (JsonNode child : node) {
                JsonNode found = findField(child, fieldName);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findField(child, fieldName);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Tạo bản sao node với field được sắp xếp alphabet đệ quy. */
    private JsonNode sortNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                sorted.set(name, sortNode(node.get(name)));
            }
            return sorted;
        } else if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            for (JsonNode element : node) {
                array.add(sortNode(element));
            }
            return array;
        }
        return node;
    }
}
