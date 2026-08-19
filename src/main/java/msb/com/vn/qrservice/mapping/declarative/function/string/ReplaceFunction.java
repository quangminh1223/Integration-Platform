package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code REPLACE(str, search, replacement)} — thay thế toàn bộ chuỗi con khớp. */
@Component
public class ReplaceFunction implements TransformFunction {

    @Override
    public String name() {
        return "REPLACE";
    }

    @Override
    public int argCount() {
        return 3;
    }

    @Override
    public JsonNode apply(List<JsonNode> args) {
        JsonNode strNode = args.get(0);
        if (strNode == null || strNode.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        String str = strNode.asText();
        String search = args.get(1).asText();
        String replacement = args.get(2).asText();
        return JsonNodeFactory.instance.textNode(str.replace(search, replacement));
    }
}
