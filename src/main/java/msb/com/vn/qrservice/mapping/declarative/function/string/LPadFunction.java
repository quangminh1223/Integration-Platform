package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code LPAD(str, length, padChar)} — đệm ký tự vào đầu chuỗi cho đủ độ dài.
 * Dùng nhiều để chuẩn hoá số tài khoản/mã giao dịch về độ dài cố định (vd đệm '0').
 * Chuỗi đã đủ hoặc vượt độ dài thì giữ nguyên, không cắt.
 */
@Component
public class LPadFunction implements TransformFunction {

    @Override
    public String name() {
        return "LPAD";
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
        int targetLength = args.get(1).asInt();
        String padChar = args.get(2).asText();
        if (padChar.isEmpty()) {
            padChar = " ";
        }

        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < targetLength) {
            sb.insert(0, padChar.charAt(0));
        }
        return JsonNodeFactory.instance.textNode(sb.toString());
    }
}
