package msb.com.vn.qrservice.qrformat.generator;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.qrformat.model.QrFieldDefinition;
import msb.com.vn.qrservice.qrformat.model.QrFormatType;
import msb.com.vn.qrservice.qrformat.model.QrStandardDefinition;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.StringJoiner;

/**
 * Sinh nội dung dạng cặp {@code key<delimiter>value} nối bằng separator.
 *
 * <p>Ví dụ chuẩn WIFI: {@code WIFI:S:MyNet;T:WPA;P:secret;;} hoặc query-string
 * {@code a=1&b=2}. Key xuất ra lấy từ {@code field.tag} (fallback {@code field.key}).</p>
 */
@Slf4j
@Component
public class KeyValueContentGenerator extends AbstractQrContentGenerator {

    @Override
    public QrFormatType supports() {
        return QrFormatType.KEY_VALUE;
    }

    @Override
    public String generate(QrStandardDefinition definition, java.util.Map<String, Object> data) {
        String separator = definition.getSeparator() != null ? definition.getSeparator() : "&";
        String kvDelim = definition.getKeyValueDelimiter() != null ? definition.getKeyValueDelimiter() : "=";

        StringJoiner joiner = new StringJoiner(separator);
        definition.getFields().stream()
                .sorted(Comparator.comparingInt(QrFieldDefinition::getOrder))
                .forEach(field -> {
                    String value = resolveValue(field, data);
                    if (value != null) {
                        String key = (field.getTag() != null && !field.getTag().isBlank())
                                ? field.getTag() : field.getKey();
                        joiner.add(key + kvDelim + value);
                    }
                });

        String content = wrap(definition, joiner.toString());
        log.debug("Sinh nội dung KEY_VALUE [{}]: length={}", definition.getId(), content.length());
        return content;
    }

    private String wrap(QrStandardDefinition def, String body) {
        return (def.getPrefix() != null ? def.getPrefix() : "")
                + body
                + (def.getSuffix() != null ? def.getSuffix() : "");
    }
}
