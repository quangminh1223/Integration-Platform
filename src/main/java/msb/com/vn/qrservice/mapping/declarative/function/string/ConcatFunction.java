package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code CONCAT(a, b, ...)} — ghép chuỗi, tối thiểu 2 tham số, không giới hạn số lượng.
 *
 * <p>Tham số null được coi như chuỗi rỗng khi ghép (đúng ngữ nghĩa ESQL CONCAT, khác với
 * hàm 1-tham-số như UPPER/TRIM trả null khi gặp null) — để tránh việc chỉ vì một field
 * tùy chọn rỗng mà cả chuỗi kết quả biến thành null.</p>
 */
@Component
public class ConcatFunction implements TransformFunction {

    @Override
    public String name() {
        return "CONCAT";
    }

    @Override
    public int argCount() {
        return -1;
    }

    @Override
    public int minArgCount() {
        return 2;
    }

    @Override
    public JsonNode apply(List<JsonNode> args) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode arg : args) {
            if (arg != null && !arg.isNull()) {
                sb.append(arg.asText());
            }
        }
        return JsonNodeFactory.instance.textNode(sb.toString());
    }
}
