package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thực thi {@link ApiMappingDefinition} — mapping field theo khai báo YAML, không cần
 * viết class Java hay DTO cho từng API backend.
 *
 * <h3>Vì sao tách khỏi cách viết tay trước đây</h3>
 * <p>Cách cũ ({@code TransferMappingService}) có hai lỗi cùng gốc: dùng một hàm
 * {@code getString(input, key, default)} cho MỌI field, bất kể field đó có bắt buộc
 * về nghiệp vụ hay không:</p>
 * <pre>
 * body.put("msbTransCode", getString(input, "tranCode", "PAYOO001")); // field bắt buộc
 * body.put("msbChannel", getString(input, "channel", ""));            // field tùy chọn
 * </pre>
 * <p>Cả hai trông giống nhau nên rất dễ để lọt field bắt buộc vào nhóm "có default", và khi
 * caller quên gửi {@code tranCode}, hệ thống gửi thẳng {@code "PAYOO001"} sang backend — sai
 * dữ liệu giao dịch mà không có lỗi nào được ném ra.</p>
 *
 * <p>Engine này buộc người khai báo phải chọn RÕ một trong ba, không có lựa chọn ngầm định
 * mập mờ: {@code required: true} (thiếu → lỗi), {@code defaultValue} (thiếu → dùng giá trị
 * mặc định NGHIỆP VỤ, không phải giá trị giả), hoặc không khai gì cả (thiếu → bỏ qua field).</p>
 */
@Slf4j
@Component
public class DeclarativeMappingEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Chiều REQUEST: input nội bộ → JSON body gửi backend ────────────────

    /**
     * Dựng JSON body gửi backend từ input nội bộ, theo danh sách {@link FieldMapping}.
     *
     * @param operationId dùng để log và gắn vào exception nếu có field bắt buộc thiếu
     * @param mappings    danh sách mapping, đọc từ {@link ApiMappingDefinition#getRequest()}
     * @param input       dữ liệu đầu vào dạng Map lồng nhau
     * @return JSON body đã build, sẵn sàng gửi backend
     * @throws FieldMappingException nếu có field {@code required=true} mà không có giá trị
     *                                sau khi đã thử fallback và default
     */
    public ObjectNode buildRequest(String operationId, List<FieldMapping> mappings,
                                   Map<String, Object> input) {
        ObjectNode root = objectMapper.createObjectNode();
        List<String> missingRequired = new ArrayList<>();

        for (FieldMapping mapping : mappings) {
            String resolved = resolveRequestValue(input, mapping);

            if (resolved == null) {
                if (mapping.isRequired()) {
                    missingRequired.add(describeField(mapping));
                }
                // Không required và không có giá trị → bỏ qua hoàn toàn, không ghi key rỗng
                continue;
            }

            String finalValue = applyMaxLength(resolved, mapping.getMaxLength());
            PathAccessor.writeToNode(root, mapping.getTarget(), finalValue);
        }

        if (!missingRequired.isEmpty()) {
            log.warn("Mapping request thất bại cho operationId={}: thiếu {} field bắt buộc: {}",
                    operationId, missingRequired.size(), missingRequired);
            throw new FieldMappingException(operationId, FieldMappingException.Phase.REQUEST,
                    missingRequired);
        }

        return root;
    }

    /**
     * Giải quyết giá trị cho một field mapping theo đúng thứ tự ưu tiên:
     * source → fallbackSources (theo thứ tự khai báo) → defaultValue.
     * Không có bước nào "đoán" giá trị ngoài các bước đã khai báo rõ.
     */
    private String resolveRequestValue(Map<String, Object> input, FieldMapping mapping) {
        String value = toTrimmedString(PathAccessor.readFromMap(input, mapping.getSource()), mapping.isTrim());
        if (isPresent(value)) {
            return value;
        }

        if (mapping.getFallbackSources() != null) {
            for (String fallbackPath : mapping.getFallbackSources()) {
                String fallbackValue = toTrimmedString(
                        PathAccessor.readFromMap(input, fallbackPath), mapping.isTrim());
                if (isPresent(fallbackValue)) {
                    return fallbackValue;
                }
            }
        }

        if (mapping.getDefaultValue() != null) {
            return mapping.getDefaultValue();
        }

        return null;
    }

    // ─── Chiều RESPONSE: JSON backend trả về → output đơn giản ──────────────

    /**
     * Mapping response backend sang Map kết quả, theo danh sách {@link FieldMapping}.
     *
     * <p>Khác với chiều request, field response bắt buộc thiếu nghĩa là BACKEND vi phạm
     * hợp đồng đã khai báo (đổi tên field, đổi cấu trúc response...) — lỗi này phải được
     * phát hiện ngay tại đây, không phải để lộ ra dưới dạng {@code transactionId: ""}
     * khiến luồng phía sau tưởng giao dịch thành công với dữ liệu trống.</p>
     *
     * @throws FieldMappingException nếu có field {@code required=true} mà backend không trả về
     */
    public Map<String, Object> buildResponse(String operationId, List<FieldMapping> mappings,
                                              JsonNode backendResponse) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> missingRequired = new ArrayList<>();

        for (FieldMapping mapping : mappings) {
            String resolved = resolveResponseValue(backendResponse, mapping);

            if (resolved == null) {
                if (mapping.isRequired()) {
                    missingRequired.add(describeField(mapping));
                }
                continue;
            }

            result.put(mapping.getTarget(), applyMaxLength(resolved, mapping.getMaxLength()));
        }

        if (!missingRequired.isEmpty()) {
            log.error("Backend vi phạm hợp đồng response cho operationId={}: thiếu {} field: {}",
                    operationId, missingRequired.size(), missingRequired);
            throw new FieldMappingException(operationId, FieldMappingException.Phase.RESPONSE,
                    missingRequired);
        }

        return result;
    }

    private String resolveResponseValue(JsonNode backendResponse, FieldMapping mapping) {
        String value = toTrimmedString(
                PathAccessor.readTextFromNode(backendResponse, mapping.getSource()), mapping.isTrim());
        if (isPresent(value)) {
            return value;
        }

        if (mapping.getFallbackSources() != null) {
            for (String fallbackPath : mapping.getFallbackSources()) {
                String fallbackValue = toTrimmedString(
                        PathAccessor.readTextFromNode(backendResponse, fallbackPath), mapping.isTrim());
                if (isPresent(fallbackValue)) {
                    return fallbackValue;
                }
            }
        }

        if (mapping.getDefaultValue() != null) {
            return mapping.getDefaultValue();
        }

        return null;
    }

    // ─── Helpers dùng chung ──────────────────────────────────────────────────

    private Object readInput(Map<String, Object> input, String path) {
        return PathAccessor.readFromMap(input, path);
    }

    private String toTrimmedString(Object value, boolean trim) {
        if (value == null) {
            return null;
        }
        String str = value.toString();
        return trim ? str.trim() : str;
    }

    /**
     * "Có giá trị" nghĩa là khác null VÀ khác chuỗi rỗng. Một field gửi lên là chuỗi rỗng
     * được coi như không có dữ liệu — tránh backend nhận field bắt buộc dạng {@code ""}.
     */
    private boolean isPresent(String value) {
        return value != null && !value.isEmpty();
    }

    private String applyMaxLength(String value, Integer maxLength) {
        if (maxLength == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String describeField(FieldMapping mapping) {
        return mapping.getSource() + " → " + mapping.getTarget();
    }
}
