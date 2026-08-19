package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Cây biểu thức cho vế phải của một dòng gán {@code .esql}. Ba dạng duy nhất được hỗ trợ:
 *
 * <pre>
 * FieldRef      InputRoot.JSON.Data.Body.chargeCollection.glAccount
 * Literal       'VND'  |  123  |  TRUE  |  NULL
 * FunctionCall  UPPER(TRIM(InputRoot.JSON.Data.Body.name))     ← lồng hàm tuỳ ý
 * </pre>
 *
 * <p>Không có toán tử số học, không {@code IF/CASE}, không truy cập biến ESQL khác ngoài
 * {@code InputRoot}. Đây là tập con có chủ đích cho field-to-field mapping.</p>
 */
sealed interface JsonToJsonExpression {

    /** Tham chiếu tới field trong input, vd {@code Body.chargeCollection.glAccount} (đã bỏ prefix). */
    record FieldRef(String path) implements JsonToJsonExpression {
    }

    /** Giá trị hằng khai báo ngay trong file — chuỗi, số, boolean, hoặc null. */
    record Literal(JsonNode value) implements JsonToJsonExpression {
    }

    /** Gọi hàm, tham số là biểu thức con — cho phép lồng bất kỳ độ sâu. */
    record FunctionCall(String functionName, List<JsonToJsonExpression> arguments) implements JsonToJsonExpression {
    }
}
