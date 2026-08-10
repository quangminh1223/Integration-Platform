package msb.com.vn.qrservice.qrformat.generator;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.QrGenerationException;
import msb.com.vn.qrservice.qrformat.model.QrFieldDefinition;
import msb.com.vn.qrservice.qrformat.model.QrFormatType;
import msb.com.vn.qrservice.qrformat.model.QrStandardDefinition;
import msb.com.vn.qrservice.qrformat.util.Crc16Util;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Sinh nội dung theo chuẩn EMVCo TLV (Tag-Length-Value) — dùng cho VietQR / EMV Merchant QR.
 *
 * <p>Mỗi field xuất ra dạng {@code tag + length(2 chữ số) + value}. Field có {@code children}
 * sẽ build value lồng nhau từ TLV của các con. Nếu {@code crcEnabled}, CRC16-CCITT được
 * tính trên toàn bộ nội dung (kèm tag CRC + "04") và nối vào cuối.</p>
 */
@Slf4j
@Component
public class EmvTlvContentGenerator extends AbstractQrContentGenerator {

    @Override
    public QrFormatType supports() {
        return QrFormatType.EMV_TLV;
    }

    @Override
    public String generate(QrStandardDefinition definition, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();

        List<QrFieldDefinition> fields = definition.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new QrGenerationException("Chuẩn EMV_TLV phải có ít nhất một field");
        }

        fields.stream()
                .sorted(Comparator.comparingInt(QrFieldDefinition::getOrder))
                .forEach(field -> {
                    String tlv = buildField(field, data);
                    if (tlv != null) {
                        sb.append(tlv);
                    }
                });

        if (definition.isCrcEnabled()) {
            String crcTag = definition.getCrcTag() != null ? definition.getCrcTag() : "63";
            sb.append(crcTag).append("04");
            sb.append(Crc16Util.crc16Ccitt(sb.toString()));
        }

        log.debug("Sinh nội dung EMV_TLV [{}]: length={}", definition.getId(), sb.length());
        return sb.toString();
    }

    /** Build một field TLV (đệ quy nếu có children). Trả về null nếu field rỗng & không bắt buộc. */
    private String buildField(QrFieldDefinition field, Map<String, Object> data) {
        String value;

        if (field.getChildren() != null && !field.getChildren().isEmpty()) {
            StringBuilder nested = new StringBuilder();
            field.getChildren().stream()
                    .sorted(Comparator.comparingInt(QrFieldDefinition::getOrder))
                    .forEach(child -> {
                        String childTlv = buildField(child, data);
                        if (childTlv != null) {
                            nested.append(childTlv);
                        }
                    });
            value = nested.toString();
            if (value.isEmpty()) {
                if (field.isRequired()) {
                    throw new QrGenerationException(
                            "Field lồng '" + field.getKey() + "' không có dữ liệu con");
                }
                return null;
            }
        } else {
            value = resolveValue(field, data);
            if (value == null) {
                return null;
            }
        }

        return tlv(field.getTag(), value);
    }

    /** {@code tag + length(2 chữ số) + value}. */
    private String tlv(String tag, String value) {
        if (tag == null || tag.isBlank()) {
            throw new QrGenerationException("Field EMV_TLV thiếu tag");
        }
        if (value.length() > 99) {
            throw new QrGenerationException(
                    "Giá trị TLV của tag '" + tag + "' vượt 99 ký tự (length 2 chữ số)");
        }
        return tag + String.format("%02d", value.length()) + value;
    }
}
