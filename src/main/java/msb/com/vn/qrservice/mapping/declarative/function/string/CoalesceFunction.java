package msb.com.vn.qrservice.mapping.declarative.function.string;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code COALESCE(a, b, c, ...)} — trả về giá trị đầu tiên khác null/rỗng.
 * Nhận tối thiểu 2 tham số, không giới hạn số lượng. Dùng để khai báo chuỗi ưu tiên
 * nguồn dữ liệu ngay trong biểu thức, thay vì phải dùng {@code fallbackSources} riêng.
 */
@Component
public class CoalesceFunction implements TransformFunction {

    @Override
    public String name() {
        return "COALESCE";
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
        for (JsonNode arg : args) {
            if (arg != null && !arg.isNull()
                    && !(arg.isTextual() && arg.asText().isEmpty())) {
                return arg;
            }
        }
        return JsonNodeFactory.instance.nullNode();
    }
}
