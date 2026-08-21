package msb.com.vn.jsontransform;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Cây biểu thức cho vế phải của một dòng gán. Ba dạng duy nhất được hỗ trợ:
 *
 * <pre>
 * FieldRef      InputRoot.JSON.Data.Body.chargeCollection.glAccount
 * Literal       'VND'  |  123  |  TRUE  |  NULL
 * FunctionCall  UPPER(TRIM(InputRoot.JSON.Data.Body.name))     &lt;- lồng hàm tuỳ ý độ sâu
 * </pre>
 *
 * <p>Không có toán tử số học, không {@code IF/CASE}, không truy cập biến nào ngoài
 * {@code InputRoot}. Đây là tập con có chủ đích cho field-to-field mapping, không phải
 * một ESQL interpreter đầy đủ.</p>
 */
sealed interface TransformExpression {

    /** Tham chiếu field trong input, path đã bỏ tiền tố {@code InputRoot.JSON.Data.} */
    record FieldRef(String path) implements TransformExpression {
    }

    /** Giá trị hằng khai báo trực tiếp trong script. */
    record Literal(JsonNode value) implements TransformExpression {
    }

    /** Lời gọi hàm; tham số là biểu thức con nên lồng được bất kỳ độ sâu. */
    record FunctionCall(String functionName, List<TransformExpression> arguments)
            implements TransformExpression {
    }
}
