package msb.com.vn.qrservice.qrformat.generator;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.QrGenerationException;
import msb.com.vn.qrservice.qrformat.model.QrFieldDefinition;
import msb.com.vn.qrservice.qrformat.model.QrFormatType;
import msb.com.vn.qrservice.qrformat.model.QrStandardDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Lấy nguyên một field nội dung làm payload — không biến đổi.
 *
 * <p>Field nội dung xác định bởi {@code rawContentKey}; nếu không cấu hình,
 * dùng field đầu tiên trong danh sách.</p>
 */
@Slf4j
@Component
public class RawContentGenerator extends AbstractQrContentGenerator {

    @Override
    public QrFormatType supports() {
        return QrFormatType.RAW;
    }

    @Override
    public String generate(QrStandardDefinition definition, Map<String, Object> data) {
        QrFieldDefinition field = resolveContentField(definition);
        String value = resolveValue(field, data);
        if (value == null) {
            value = "";
        }
        String content = (definition.getPrefix() != null ? definition.getPrefix() : "")
                + value
                + (definition.getSuffix() != null ? definition.getSuffix() : "");
        log.debug("Sinh nội dung RAW [{}]: length={}", definition.getId(), content.length());
        return content;
    }

    private QrFieldDefinition resolveContentField(QrStandardDefinition definition) {
        if (definition.getFields() == null || definition.getFields().isEmpty()) {
            throw new QrGenerationException("Chuẩn RAW phải có ít nhất một field nội dung");
        }
        String key = definition.getRawContentKey();
        if (key != null && !key.isBlank()) {
            return definition.getFields().stream()
                    .filter(f -> key.equals(f.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new QrGenerationException(
                            "Không tìm thấy field nội dung RAW '" + key + "'"));
        }
        return definition.getFields().get(0);
    }
}
