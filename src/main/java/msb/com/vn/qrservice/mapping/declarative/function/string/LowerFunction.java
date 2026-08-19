package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code LOWER(str)} — chuyển toàn bộ ký tự thành thường. Null vào → null ra. */
@Component
public class LowerFunction implements TransformFunction {

    @Override
    public String name() {
        return "LOWER";
    }

    @Override
    public int argCount() {
        return 1;
    }

    @Override
    public JsonNode apply(List<JsonNode> args) {
        JsonNode value = args.get(0);
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        return JsonNodeFactory.instance.textNode(value.asText().toLowerCase());
    }
}
