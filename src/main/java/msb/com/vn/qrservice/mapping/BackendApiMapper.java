package msb.com.vn.qrservice.mapping;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Interface chung cho tất cả API mapping.
 *
 * Mỗi API backend (operationId) implement 1 class riêng.
 * Khi thêm API mới → tạo 1 class mới implement interface này → xong.
 *
 * Pattern: Strategy + Auto-registration via Spring.
 */
public interface BackendApiMapper {

    /**
     * operationId trong swagger — dùng để lookup mapper.
     * Ví dụ: "createMsbAccountTransfer", "getMsbAccountBalance"
     */
    String getOperationId();

    /**
     * Mapping input nội bộ → payload gửi sang backend (theo swagger schema).
     *
     * @param input  dữ liệu đầu vào từ hệ thống nội bộ (flat JSON)
     * @return payload đúng format swagger (header + body)
     */
    Object buildPayload(Map<String, Object> input);

    /**
     * Mapping response backend → output đơn giản trả về cho caller.
     *
     * @param backendResponse  response JSON từ backend
     * @param originalInput    input gốc (để fallback nếu cần)
     * @return output đã mapping
     */
    Map<String, Object> buildResponse(JsonNode backendResponse, Map<String, Object> originalInput);

    /**
     * Có path params không? Nếu có thì extract từ input.
     * Mặc định: không có path params.
     */
    default Map<String, Object> getPathParams(Map<String, Object> input) {
        return Map.of();
    }

    /**
     * Mô tả ngắn về API này (dùng cho logging/debug).
     */
    default String getDescription() {
        return getOperationId();
    }
}
