package msb.com.vn.qrservice.qrformat.generator;

import msb.com.vn.qrservice.common.exception.QrGenerationException;
import msb.com.vn.qrservice.qrformat.model.QrFieldDefinition;

import java.util.Map;

/**
 * Logic chung cho các generator: phân giải giá trị field, áp dụng
 * fixed/default, validate required & độ dài, cắt theo maxLength.
 */
public abstract class AbstractQrContentGenerator implements QrContentGenerator {

    /**
     * Phân giải giá trị cuối cùng của một field theo thứ tự ưu tiên:
     * fixedValue → data[key] → defaultValue. Có validate và cắt độ dài.
     *
     * @return giá trị đã chuẩn hóa, hoặc {@code null} nếu field không bắt buộc và không có giá trị.
     */
    protected String resolveValue(QrFieldDefinition field, Map<String, Object> data) {
        String value;
        if (field.getFixedValue() != null) {
            value = field.getFixedValue();
        } else {
            Object raw = (data != null) ? data.get(field.getKey()) : null;
            value = (raw != null) ? String.valueOf(raw) : field.getDefaultValue();
        }

        if (value == null || value.isEmpty()) {
            if (field.isRequired()) {
                throw new QrGenerationException(
                        "Thiếu giá trị bắt buộc cho field '" + field.getKey() + "'");
            }
            return null;
        }

        if (field.getMinLength() != null && value.length() < field.getMinLength()) {
            throw new QrGenerationException(
                    "Field '" + field.getKey() + "' ngắn hơn độ dài tối thiểu " + field.getMinLength());
        }

        if (field.getMaxLength() != null && value.length() > field.getMaxLength()) {
            value = value.substring(0, field.getMaxLength());
        }

        return value;
    }
}
