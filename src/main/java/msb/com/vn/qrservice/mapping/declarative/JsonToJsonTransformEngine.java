package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunctionRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Thực thi {@link JsonToJsonTransformDefinition} — chạy từng dòng gán theo đúng thứ tự
 * khai báo trong file {@code .esql}, đọc từ input JSON, ghi vào output JSON.
 *
 * <p>Đây là bước TRANSFORMATION thuần JSON→JSON, khác với {@link DeclarativeMappingEngine}
 * (dùng cho mapping input nội bộ → gọi backend qua {@code ResilientBackendCaller}). Hai engine
 * độc lập, dùng cho hai mục đích khác nhau, có thể phối hợp: transform trước, rồi gọi backend
 * bằng kết quả đã transform.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class JsonToJsonTransformEngine {

    private final TransformFunctionRegistry functionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Chạy toàn bộ transform, trả về JSON output đã dựng.
     *
     * @throws JsonToJsonTransformException nếu một dòng {@code required} resolve ra null
     */
    ObjectNode execute(JsonToJsonTransformDefinition definition, JsonNode input) {
        ObjectNode output = objectMapper.createObjectNode();
        List<String> missingRequired = new ArrayList<>();

        for (JsonToJsonAssignment assignment : definition.assignments()) {
            JsonNode value = evaluate(assignment.expression(), input, definition.operationId());

            if (value == null || value.isNull()) {
                if (assignment.required()) {
                    missingRequired.add(assignment.rawStatement());
                }
                continue; // không required → bỏ qua field, không ghi null vào output
            }

            PathAccessor.writeToNode(output, assignment.targetPath(), value);
        }

        if (!missingRequired.isEmpty()) {
            throw new JsonToJsonTransformException(definition.operationId(), missingRequired);
        }

        return output;
    }

    /**
     * Đánh giá một biểu thức, đệ quy cho FunctionCall lồng nhau.
     */
    private JsonNode evaluate(JsonToJsonExpression expression, JsonNode input, String operationId) {
        return switch (expression) {
            case JsonToJsonExpression.Literal literal -> literal.value();

            case JsonToJsonExpression.FieldRef fieldRef ->
                    PathAccessor.readFromNode(input, fieldRef.path());

            case JsonToJsonExpression.FunctionCall call -> {
                List<JsonNode> args = new ArrayList<>(call.arguments().size());
                for (JsonToJsonExpression arg : call.arguments()) {
                    args.add(evaluate(arg, input, operationId));
                }

                // Đã validate tên hàm lúc parse (JsonToJsonExpressionParser), nên ở đây
                // luôn tìm thấy — nếu không thì là lỗi logic nội bộ, không phải lỗi input.
                TransformFunction function = functionRegistry.find(call.functionName())
                        .orElseThrow(() -> new IllegalStateException(
                                "Hàm '" + call.functionName() + "' đã qua validate lúc parse "
                                        + "nhưng không tìm thấy lúc thực thi — lỗi nội bộ registry"));

                yield function.apply(args);
            }
        };
    }
}
