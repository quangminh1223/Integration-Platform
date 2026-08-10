package msb.com.vn.qrservice.common.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Utility xử lý input HTTP linh hoạt — tự động nhận dạng và convert
 * body từ bất kỳ dạng nào sang kiểu mong muốn.
 *
 * <h3>Các dạng input được hỗ trợ:</h3>
 * <pre>
 * // 1. JSON object thuần
 * {"creditAccount":"80000002233","debitAmount":"98000"}
 *
 * // 2. JSON string (escaped) — hay gặp khi client serialize 2 lần
 * "{\"creditAccount\":\"80000002233\",\"debitAmount\":\"98000\"}"
 *
 * // 3. JSON string trong field của object
 * {"data": "{\"creditAccount\":\"80000002233\"}"}
 *
 * // 4. Plain string
 * "hello world"
 *
 * // 5. Map / POJO trực tiếp
 * Map.of("creditAccount", "80000002233")
 * </pre>
 */
@Slf4j
@UtilityClass
public class HttpInputResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─── Resolve từ FlexibleBody ──────────────────────────────────────────────

    /**
     * Convert FlexibleBody sang POJO.
     * Tự động xử lý: JSON object, JSON string, escaped JSON.
     */
    public static <T> T resolve(FlexibleBody body, Class<T> targetType) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Request body không được để trống");
        }
        log.debug("Resolving FlexibleBody → {} | type={}", targetType.getSimpleName(), body);
        return body.toObject(targetType);
    }

    /**
     * Convert FlexibleBody sang JsonNode (không cần biết kiểu trước).
     */
    public static JsonNode resolveToNode(FlexibleBody body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Request body không được để trống");
        }
        return body.asJsonNode();
    }

    /**
     * Convert FlexibleBody sang Map<String, Object>.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveToMap(FlexibleBody body) {
        JsonNode node = resolveToNode(body);
        if (!node.isObject()) {
            throw new IllegalArgumentException(
                    "Body phải là JSON object để convert sang Map, nhận được: " + node.getNodeType());
        }
        return MAPPER.convertValue(node, Map.class);
    }

    // ─── Resolve từ raw String ────────────────────────────────────────────────

    /**
     * Convert raw String sang POJO.
     * Tự động xử lý: JSON string, escaped JSON string, plain JSON.
     *
     * <pre>
     * // Cả 3 dạng đều hoạt động:
     * resolve("{\"key\":\"val\"}", MyDto.class)
     * resolve("\"{\\"key\\":\\"val\\"}\"", MyDto.class)  // double-escaped
     * </pre>
     */
    public static <T> T resolve(String rawInput, Class<T> targetType) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("Input string không được để trống");
        }
        log.debug("Resolving String → {}", targetType.getSimpleName());
        JsonNode node = parseStringToNode(rawInput);
        try {
            return MAPPER.treeToValue(node, targetType);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Không thể convert input sang " + targetType.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convert raw String sang JsonNode.
     * Tự động unwrap nếu là JSON string bị escape.
     */
    public static JsonNode resolveToNode(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("Input string không được để trống");
        }
        return parseStringToNode(rawInput);
    }

    // ─── Resolve từ Object (unknown type) ────────────────────────────────────

    /**
     * Convert Object bất kỳ (Map, String, POJO...) sang kiểu target.
     * Hữu ích khi nhận từ Kafka message, Redis cache, hoặc external system.
     *
     * <pre>
     * Object fromKafka = ...; // có thể là Map hoặc String
     * MyDto dto = HttpInputResolver.resolve(fromKafka, MyDto.class);
     * </pre>
     */
    public static <T> T resolve(Object input, Class<T> targetType) {
        if (input == null) {
            throw new IllegalArgumentException("Input không được null");
        }
        // Nếu đã đúng kiểu → cast thẳng
        if (targetType.isInstance(input)) {
            return targetType.cast(input);
        }
        // Nếu là String → dùng path xử lý string
        if (input instanceof String str) {
            return resolve(str, targetType);
        }
        // Map, POJO khác → convert qua Jackson
        try {
            return MAPPER.convertValue(input, targetType);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Không thể convert " + input.getClass().getSimpleName()
                    + " sang " + targetType.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Parse string thành JsonNode, tự động unwrap nếu bị double-escape.
     *
     * Ví dụ:
     * - {@code {"a":"b"}}         → ObjectNode
     * - {@code "{\"a\":\"b\"}"}   → ObjectNode (unwrap TextNode)
     * - {@code "hello"}           → TextNode
     */
    private static JsonNode parseStringToNode(String input) {
        String trimmed = input.trim();
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            // Nếu parse ra TextNode → thử parse lần 2 (double-escaped)
            if (node.isTextual()) {
                String inner = node.asText().trim();
                try {
                    JsonNode innerNode = MAPPER.readTree(inner);
                    // Chỉ unwrap nếu inner là object/array (tránh unwrap số/boolean)
                    if (innerNode.isObject() || innerNode.isArray()) {
                        log.debug("Detected double-escaped JSON, unwrapping...");
                        return innerNode;
                    }
                } catch (Exception ignored) {
                    // inner không phải JSON → giữ nguyên TextNode
                }
            }
            return node;
        } catch (Exception e) {
            // Không parse được → wrap thành TextNode
            log.debug("Input không phải JSON hợp lệ, wrap thành TextNode: {}", trimmed);
            return MAPPER.getNodeFactory().textNode(trimmed);
        }
    }
}
