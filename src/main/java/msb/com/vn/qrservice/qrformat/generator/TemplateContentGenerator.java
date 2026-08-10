package msb.com.vn.qrservice.qrformat.generator;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.QrGenerationException;
import msb.com.vn.qrservice.qrformat.model.QrFieldDefinition;
import msb.com.vn.qrservice.qrformat.model.QrFormatType;
import msb.com.vn.qrservice.qrformat.model.QrStandardDefinition;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Sinh nội dung bằng cách thay placeholder {@code {key}} trong template bằng giá trị field.
 *
 * <p>Ví dụ template URL: {@code https://pay.example.com?to={account}&amount={amount}}.</p>
 */
@Slf4j
@Component
public class TemplateContentGenerator extends AbstractQrContentGenerator {

    @Override
    public QrFormatType supports() {
        return QrFormatType.TEMPLATE;
    }

    @Override
    public String generate(QrStandardDefinition definition, Map<String, Object> data) {
        String template = definition.getTemplate();
        if (template == null || template.isBlank()) {
            throw new QrGenerationException("Chuẩn TEMPLATE thiếu cấu hình 'template'");
        }

        // Tập giá trị đã chuẩn hóa theo định nghĩa field (fixed/default/validate).
        Map<String, String> resolved = new HashMap<>();
        if (definition.getFields() != null) {
            for (QrFieldDefinition field : definition.getFields()) {
                String value = resolveValue(field, data);
                resolved.put(field.getKey(), value != null ? value : "");
            }
        }

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (end < 0) {
                    throw new QrGenerationException("Template lỗi: thiếu '}' đóng placeholder");
                }
                String key = template.substring(i + 1, end);
                String value = resolved.containsKey(key)
                        ? resolved.get(key)
                        : (data != null && data.get(key) != null ? String.valueOf(data.get(key)) : "");
                out.append(value);
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }

        String content = out.toString();
        log.debug("Sinh nội dung TEMPLATE [{}]: length={}", definition.getId(), content.length());
        return content;
    }
}
