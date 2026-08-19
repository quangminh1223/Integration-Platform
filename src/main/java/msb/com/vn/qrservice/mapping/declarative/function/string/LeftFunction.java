package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code LEFT(str, n)} — lấy n ký tự đầu. Chuỗi ngắn hơn n thì trả về nguyên chuỗi. */
@Component
public class LeftFunction implements TransformFunction {

    @Override
    public String name() {
        return "LEFT";
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
        return JsonNodeFactory.instance.textNode(str.substring(0, Math.min(n, str.length())));
    }
}
