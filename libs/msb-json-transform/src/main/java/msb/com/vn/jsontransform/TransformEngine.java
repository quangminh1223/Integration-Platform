package msb.com.vn.jsontransform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Chạy từng dòng gán của một {@link JsonTransformDefinition} theo đúng thứ tự khai báo:
 * đọc từ input JSON, ghi vào output JSON.
 *
 * <p>Field không {@code required} mà resolve ra null thì BỎ QUA hoàn toàn — không ghi
 * {@code null} vào output. Nhờ vậy output chỉ chứa field thực sự có dữ liệu, bên nhận
 * không phải phân biệt "field vắng mặt" với "field có nhưng null".</p>
 */
final class TransformEngine {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final TransformFunctionRegistry functionRegistry;

    TransformEngine(TransformFunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }

    /**
     * @throws JsonTransformException nếu một dòng {@code required} resolve ra null
     */
    ObjectNode execute(JsonTransformDefinition definition, JsonNode input) {
        ObjectNode output = NODES.objectNode();
        List<String> missingRequired = new ArrayList<>();

        for (TransformAssignment assignment : definition.assignments()) {
            JsonNode value = evaluate(assignment.expression(), input);

            if (value == null || value.isNull()) {
                if (assignment.required()) {
                    missingRequired.add(assignment.rawStatement());
                }
                continue;
            }

            PathAccessor.writeToNode(output, assignment.targetPath(), value);
        }

        if (!missingRequired.isEmpty()) {
            throw new JsonTransformException(definition.operationId(), missingRequired);
        }

        return output;
    }

    /**
     * Đánh giá một biểu thức, đệ quy cho FunctionCall lồng nhau.
     *
     * <p>Dùng {@code instanceof} pattern (Java 16+) thay vì pattern matching trong
     * {@code switch} (Java 21+) để lib biên dịch được ở mức Java 17, dùng lại được cho
     * project chưa lên 21.</p>
     */
    private JsonNode evaluate(TransformExpression expression, JsonNode input) {
        if (expression instanceof TransformExpression.Literal literal) {
            return literal.value();
        }

        if (expression instanceof TransformExpression.FieldRef fieldRef) {
            return PathAccessor.readFromNode(input, fieldRef.path());
        }

        if (expression instanceof TransformExpression.FunctionCall call) {
            List<JsonNode> args = new ArrayList<>(call.arguments().size());
            for (TransformExpression arg : call.arguments()) {
                args.add(evaluate(arg, input));
            }

            // Tên hàm và số tham số đã validate lúc parse (ExpressionParser), nên tới đây
            // luôn tìm thấy. Nếu không thì là lỗi logic nội bộ, không phải lỗi dữ liệu.
            TransformFunction function = functionRegistry.find(call.functionName())
                    .orElseThrow(() -> new IllegalStateException(
                            "Hàm '" + call.functionName() + "' đã qua validate lúc parse "
                                    + "nhưng không tìm thấy lúc chạy — lỗi nội bộ registry"));

            return function.apply(args);
        }

        throw new IllegalStateException(
                "Loại biểu thức chưa được hỗ trợ: " + expression.getClass().getName());
    }
}
