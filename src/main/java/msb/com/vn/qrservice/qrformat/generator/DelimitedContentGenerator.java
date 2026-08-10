package msb.com.vn.qrservice.qrformat.generator;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.qrformat.model.QrFieldDefinition;
import msb.com.vn.qrservice.qrformat.model.QrFormatType;
import msb.com.vn.qrservice.qrformat.model.QrStandardDefinition;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Sinh nội dung bằng cách nối các giá trị field theo thứ tự, phân tách bằng separator.
 *
 * <p>Field không bắt buộc mà rỗng vẫn xuất ra chuỗi rỗng để giữ đúng vị trí cột.</p>
 */
@Slf4j
@Component
public class DelimitedContentGenerator extends AbstractQrContentGenerator {

    @Override
    public QrFormatType supports() {
        return QrFormatType.DELIMITED;
    }

    @Override
    public String generate(QrStandardDefinition definition, Map<String, Object> data) {
        String separator = definition.getSeparator() != null ? definition.getSeparator() : "|";

        StringJoiner joiner = new StringJoiner(separator);
        definition.getFields().stream()
                .sorted(Comparator.comparingInt(QrFieldDefinition::getOrder))
                .forEach(field -> {
                    String value = resolveValue(field, data);
                    joiner.add(value != null ? value : "");
                });

        String content = (definition.getPrefix() != null ? definition.getPrefix() : "")
                + joiner
                + (definition.getSuffix() != null ? definition.getSuffix() : "");
        log.debug("Sinh nội dung DELIMITED [{}]: length={}", definition.getId(), content.length());
        return content;
    }
}
