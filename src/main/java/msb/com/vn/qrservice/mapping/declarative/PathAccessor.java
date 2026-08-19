package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc/ghi giá trị theo dot-path, dùng chung cho cả mapping request và response.
 *
 * <p>Cú pháp path: {@code a.b.c} cho object lồng nhau, {@code a.b[0].c} cho mảng.
 * Ví dụ: {@code body.paymentDetails[0].paymentDetail}.</p>
 *
 * <p>Nguyên tắc quan trọng: mọi hàm "get" trả về {@code null} khi path không tồn tại
 * hoặc giá trị rỗng — KHÔNG BAO GIỜ ném exception hay trả về chuỗi rỗng để che lấp sự khác biệt
 * giữa "không có dữ liệu" và "có dữ liệu nhưng rỗng". Việc quyết định coi null là lỗi hay
 * bỏ qua thuộc về {@link DeclarativeMappingEngine}, không phải tầng truy cập path này.</p>
 */
@UtilityClass
class PathAccessor {

    private static final Pattern ARRAY_SEGMENT = Pattern.compile("^(\\w+)\\[(\\d+)]$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─── Đọc từ Map (dùng cho input nội bộ) ─────────────────────────────────

    /**
     * Đọc giá trị theo dot-path từ Map lồng nhau.
     * Trả về {@code null} nếu bất kỳ segment nào trong path không tồn tại.
     */
    static Object readFromMap(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            Matcher arrayMatch = ARRAY_SEGMENT.matcher(segment);
            if (arrayMatch.matches()) {
                current = readIndexed(current, arrayMatch.group(1), Integer.parseInt(arrayMatch.group(2)));
            } else {
                current = readKey(current, segment);
            }
        }
        return current;
    }

    private static Object readKey(Object current, String key) {
        if (current instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object readIndexed(Object current, String key, int index) {
        Object listCandidate = readKey(current, key);
        if (listCandidate instanceof List<?> list) {
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    // ─── Đọc từ JsonNode (dùng cho response backend) ────────────────────────

    /**
     * Đọc giá trị theo dot-path từ JsonNode.
     * Trả về {@code null} nếu path không tồn tại HOẶC node là JSON null —
     * hai trường hợp này được coi là "không có dữ liệu" như nhau ở tầng này.
     */
    static JsonNode readFromNode(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            Matcher arrayMatch = ARRAY_SEGMENT.matcher(segment);
            if (arrayMatch.matches()) {
                current = current.path(arrayMatch.group(1)).path(Integer.parseInt(arrayMatch.group(2)));
            } else {
                current = current.path(segment);
            }
        }
        return (current == null || current.isMissingNode() || current.isNull()) ? null : current;
    }

    /**
     * Trả về text value tại path, hoặc {@code null}.
     * Khác {@code JsonNode.asText()} — hàm đó trả {@code ""} cho missing node,
     * ở đây phải phân biệt được "không có" và "có nhưng rỗng".
     */
    static String readTextFromNode(JsonNode root, String path) {
        JsonNode node = readFromNode(root, path);
        return node == null ? null : node.asText();
    }

    // ─── Ghi vào ObjectNode (dùng cho dựng payload gửi backend) ─────────────

    /**
     * Ghi giá trị chuỗi vào path, tự tạo object/array lồng nhau nếu chưa tồn tại.
     * Giá trị {@code null} thì KHÔNG ghi — caller phải tự quyết định có ghi hay không
     * trước khi gọi hàm này (xem {@link DeclarativeMappingEngine}).
     */
    static void writeToNode(ObjectNode root, String path, String value) {
        if (value == null) {
            return;
        }
        writeToNode(root, path, MAPPER.getNodeFactory().textNode(value));
    }

    /**
     * Ghi một {@link JsonNode} bất kỳ vào path — dùng cho transform JSON→JSON, nơi giá trị
     * copy sang có thể là object/array/number/boolean lồng nhau, không chỉ chuỗi.
     * Giá trị {@code null} thì KHÔNG ghi.
     */
    static void writeToNode(ObjectNode root, String path, JsonNode value) {
        if (value == null) {
            return;
        }
        String[] segments = path.split("\\.");
        com.fasterxml.jackson.databind.node.ContainerNode<?> current = root;

        for (int i = 0; i < segments.length - 1; i++) {
            current = descend(current, segments[i]);
        }

        String last = segments[segments.length - 1];
        Matcher arrayMatch = ARRAY_SEGMENT.matcher(last);
        if (arrayMatch.matches()) {
            ArrayNode array = ensureArray((ObjectNode) current, arrayMatch.group(1));
            int index = Integer.parseInt(arrayMatch.group(2));
            ensureArraySize(array, index);
            array.set(index, value);
        } else {
            ((ObjectNode) current).set(last, value);
        }
    }

    private static com.fasterxml.jackson.databind.node.ContainerNode<?> descend(
            com.fasterxml.jackson.databind.node.ContainerNode<?> current, String segment) {

        Matcher arrayMatch = ARRAY_SEGMENT.matcher(segment);
        if (arrayMatch.matches()) {
            ArrayNode array = ensureArray((ObjectNode) current, arrayMatch.group(1));
            int index = Integer.parseInt(arrayMatch.group(2));
            ensureArrayObjectSize(array, index);
            return (ObjectNode) array.get(index);
        }

        ObjectNode parent = (ObjectNode) current;
        JsonNode child = parent.get(segment);
        if (!(child instanceof ObjectNode)) {
            ObjectNode created = MAPPER.createObjectNode();
            parent.set(segment, created);
            return created;
        }
        return (ObjectNode) child;
    }

    private static ArrayNode ensureArray(ObjectNode parent, String key) {
        JsonNode existing = parent.get(key);
        if (existing instanceof ArrayNode array) {
            return array;
        }
        ArrayNode created = MAPPER.createArrayNode();
        parent.set(key, created);
        return created;
    }

    private static void ensureArraySize(ArrayNode array, int index) {
        while (array.size() <= index) {
            array.addNull();
        }
    }

    private static void ensureArrayObjectSize(ArrayNode array, int index) {
        while (array.size() <= index) {
            array.addObject();
        }
        if (!(array.get(index) instanceof ObjectNode)) {
            array.set(index, MAPPER.createObjectNode());
        }
    }
}
