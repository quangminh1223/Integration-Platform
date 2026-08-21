package msb.com.vn.jsontransform;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Một hàm dùng được trong biểu thức của script transform.
 *
 * <p>12 hàm có sẵn nằm gọn trong {@link StringFunctions}. Thêm hàm riêng thì implement
 * interface này rồi đăng ký qua {@link JsonTransformer#builder()}:</p>
 *
 * <pre>{@code
 * JsonTransformer t = JsonTransformer.builder()
 *         .function(new TransformFunction() {
 *             public String functionName() { return "MASK"; }
 *             public int minArgs() { return 1; }
 *             public int maxArgs() { return 1; }
 *             public JsonNode apply(List<JsonNode> args) { ... }
 *         })
 *         .build();
 * }</pre>
 */
public interface TransformFunction {

    /** Tên hàm dùng trong script. So khớp KHÔNG phân biệt hoa/thường. */
    String functionName();

    /** Số tham số tối thiểu. */
    int minArgs();

    /** Số tham số tối đa; {@code -1} nghĩa là không giới hạn (như {@code CONCAT}). */
    int maxArgs();

    /**
     * Thực thi hàm trên các tham số đã resolve.
     *
     * <p>Tham số có thể null hoặc JSON null khi field nguồn không có giá trị — hàm tự quyết
     * định ngữ nghĩa null. Số lượng tham số đã được kiểm lúc compile nên ở đây không cần
     * kiểm lại.</p>
     */
    JsonNode apply(List<JsonNode> args);
}
