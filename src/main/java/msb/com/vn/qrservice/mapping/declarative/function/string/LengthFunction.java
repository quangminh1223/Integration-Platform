package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code LENGTH(str)} — độ dài chuỗi. Null vào → 0 (khác quy tắc null-propagate, vì kết quả là số). */
@Component
public class LengthFunction implements TransformFunction {

    @Override
    public String name() {
        return "LENGTH";
    }

    @Override
    public int argCount() {
        return 1;
    }

    @Override
    public JsonNode apply(List<JsonNode> args) {
        JsonNode value = args.get(0);
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.numberNode(0);
        }
        return JsonNodeFactory.instance.numberNode(value.asText().length());
    }
}
