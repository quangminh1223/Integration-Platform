package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code SUBSTRING(str, start, length)} — cắt chuỗi con.
 *
 * <p>{@code start} đánh số từ 1 (theo chuẩn SQL/ESQL, không phải 0 như Java).
 * Vượt quá độ dài chuỗi thì cắt tới hết, không ném lỗi — cùng hành vi khoan dung
 * như ESQL gốc, tránh transform chết vì dữ liệu input ngắn hơn dự kiến.</p>
 */
@Component
public class SubstringFunction implements TransformFunction {

    @Override
    public String name() {
        return "SUBSTRING";
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
        int start = args.get(1).asInt() - 1;   // ESQL 1-based → Java 0-based
        int length = args.get(2).asInt();

        if (start < 0) {
            start = 0;
        }
        if (start >= str.length()) {
            return JsonNodeFactory.instance.textNode("");
        }

        int end = Math.min(start + length, str.length());
        return JsonNodeFactory.instance.textNode(str.substring(start, end));
    }
}
