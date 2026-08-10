package msb.com.vn.qrservice.common.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Getter;

/**
 * Wrapper cho phép API nhận body ở bất kỳ dạng nào:
 * <ul>
 *   <li>JSON object  : {@code {"key":"value"}}</li>
 *   <li>JSON array   : {@code [1,2,3]}</li>
 *   <li>JSON string  : {@code "\"hello\""} hoặc {@code "{\"key\":\"value\"}"}</li>
 *   <li>Plain string : {@code "hello world"}</li>
 * </ul>
 *
 * Jackson tự động gọi {@link #of(JsonNode)} khi deserialize,
 * nên controller chỉ cần khai báo kiểu {@code FlexibleBody} là xong.
 *
 * <pre>
 * // Controller
 * {@literal @}PostMapping("/process")
 * public ResponseEntity<?> process({@literal @}RequestBody FlexibleBody body) {
 *     JsonNode node   = body.asJsonNode();          // luôn là JsonNode
 *     String   raw    = body.asRawString();         // chuỗi gốc
 *     MyDto    dto    = body.toObject(MyDto.class); // map sang POJO
 *     boolean  isJson = body.isJsonObject();        // kiểm tra loại
 * }
 * </pre>
 */
@Getter
public class FlexibleBody {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Node gốc sau khi Jackson parse — có thể là ObjectNode, ArrayNode, TextNode... */
    private final JsonNode rawNode;

    /** Chuỗi JSON đã được normalize (nếu input là JSON string thì đã unescape) */
    private final JsonNode resolvedNode;

    private FlexibleBody(JsonNode rawNode, JsonNode resolvedNode) {
        this.rawNode      = rawNode;
        this.resolvedNode = resolvedNode;
    }

    // ─── Factory — Jackson gọi khi deserialize ────────────────────────────────

    @JsonCreator
    public static FlexibleBody of(JsonNode node) {
        if (node == null) {
            return new FlexibleBody(MAPPER.nullNode(), MAPPER.nullNode());
        }

        // Nếu Jackson parse ra TextNode → có thể là JSON string bị escape
        if (node.isTextual()) {
            String text = node.asText();
            JsonNode parsed = tryParseJson(text);
            // parsed != null nghĩa là text là JSON hợp lệ → unwrap
            return new FlexibleBody(node, parsed != null ? parsed : node);
        }

        // ObjectNode / ArrayNode / NumberNode / BooleanNode → giữ nguyên
        return new FlexibleBody(node, node);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Trả về JsonNode đã được resolve:
     * - Nếu input là JSON string → trả về node đã parse
     * - Nếu input là JSON object/array → trả về nguyên
     */
    public JsonNode asJsonNode() {
        return resolvedNode;
    }

    /**
     * Trả về chuỗi string của node đã resolve.
     * - JSON object/array → chuỗi JSON
     * - Text node → text thuần
     */
    public String asRawString() {
        if (resolvedNode.isTextual()) {
            return resolvedNode.asText();
        }
        return resolvedNode.toString();
    }

    /**
     * Map node đã resolve sang POJO.
     *
     * @throws IllegalArgumentException nếu không thể convert
     */
    public <T> T toObject(Class<T> type) {
        try {
            return MAPPER.treeToValue(resolvedNode, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Không thể convert body sang " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** true nếu body là JSON object {@code {...}} */
    public boolean isJsonObject() {
        return resolvedNode.isObject();
    }

    /** true nếu body là JSON array {@code [...]} */
    public boolean isJsonArray() {
        return resolvedNode.isArray();
    }

    /** true nếu body là plain text (không parse được thành JSON) */
    public boolean isPlainText() {
        return resolvedNode.isTextual();
    }

    /** true nếu body null hoặc rỗng */
    public boolean isEmpty() {
        return resolvedNode.isNull() || resolvedNode.isMissingNode()
                || (resolvedNode.isTextual() && resolvedNode.asText().isBlank());
    }

    // ─── Serialization ────────────────────────────────────────────────────────

    @JsonValue
    public JsonNode toJson() {
        return resolvedNode;
    }

    @Override
    public String toString() {
        return "FlexibleBody{type=" + nodeType() + ", value=" + asRawString() + "}";
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String nodeType() {
        if (resolvedNode.isObject())  return "JSON_OBJECT";
        if (resolvedNode.isArray())   return "JSON_ARRAY";
        if (resolvedNode.isTextual()) return "TEXT";
        if (resolvedNode.isNumber())  return "NUMBER";
        if (resolvedNode.isBoolean()) return "BOOLEAN";
        if (resolvedNode.isNull())    return "NULL";
        return "UNKNOWN";
    }

    /** Thử parse chuỗi thành JsonNode, trả về null nếu không phải JSON hợp lệ */
    private static JsonNode tryParseJson(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        // Chỉ thử parse nếu trông giống JSON (bắt đầu bằng { [ " hoặc số)
        char first = trimmed.charAt(0);
        if (first != '{' && first != '[' && first != '"'
                && !Character.isDigit(first) && first != '-'
                && !trimmed.equals("true") && !trimmed.equals("false")
                && !trimmed.equals("null")) {
            return null;
        }
        try {
            return MAPPER.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
    }
}
