package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code RIGHT(str, n)} — lấy n ký tự cuối. Chuỗi ngắn hơn n thì trả về nguyên chuỗi. */
@Component
public class RightFunction implements TransformFunction {

    @Override
    public String name() {
        return "RIGHT";
    }

    @Override
    public int argCount() {
        return 2;
    }

    @Override
    public JsonNode apply(List<JsonNode> args) {
        JsonNode strNode = args.get(0);
        if (strNode == null || strNode.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        String str = strNode.asText();
        int n = Math.max(0, args.get(1).asInt());
        int start = Math.max(0, str.length() - n);
        return JsonNodeFactory.instance.textNode(str.substring(start));
    }
}
