package msb.com.vn.jsontransform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc/ghi giá trị JSON theo dot-path.
 *
 * <p>Cú pháp path: {@code a.b.c} cho object lồng nhau, {@code a.b[0].c} cho mảng.
 * Ví dụ: {@code body.paymentDetails[0].paymentDetail}.</p>
 *
 * <p>Nguyên tắc: hàm đọc trả về {@code null} khi path không tồn tại hoặc node là JSON null —
 * KHÔNG ném exception và KHÔNG trả chuỗi rỗng, để không xoá mất khác biệt giữa "không có
 * dữ liệu" và "có dữ liệu nhưng rỗng". Quyết định coi null là lỗi hay bỏ qua thuộc về
 * {@link TransformEngine}, không phải tầng truy cập path này.</p>
 */
final class PathAccessor {

    private static final Pattern ARRAY_SEGMENT = Pattern.compile("^([^\\[\\]]+)\\[(\\d+)]$");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private PathAccessor() {
    }

    // ─── Đọc từ JsonNode ────────────────────────────────────────────────────

    /**
     * Đọc node theo dot-path. Trả về {@code null} nếu path không tồn tại HOẶC node là
     * JSON null — ở tầng này hai trường hợp đó tương đương "không có dữ liệu".
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
                current = current.path(arrayMatch.group(1))
                        .path(Integer.parseInt(arrayMatch.group(2)));
            } else {
                current = current.path(segment);
            }
        }
        return (current.isMissingNode() || current.isNull()) ? null : current;
    }

    // ─── Ghi vào ObjectNode ─────────────────────────────────────────────────

    /**
     * Ghi một {@link JsonNode} vào path, tự tạo object/array lồng nhau nếu chưa tồn tại.
     * Giá trị {@code null} thì KHÔNG ghi — caller phải quyết định trước khi gọi.
     */
    static void writeToNode(ObjectNode root, String path, JsonNode value) {
        if (value == null) {
            return;
        }
        String[] segments = path.split("\\.");
        ContainerNode<?> current = root;

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

    private static ContainerNode<?> descend(ContainerNode<?> current, String segment) {
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
            ObjectNode created = NODES.objectNode();
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
        ArrayNode created = NODES.arrayNode();
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
            array.set(index, NODES.objectNode());
        }
    }
}
