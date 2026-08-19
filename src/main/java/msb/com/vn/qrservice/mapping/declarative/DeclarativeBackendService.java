package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.QrException;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.swagger.service.ResilientBackendCaller;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Gọi bất kỳ API backend nào chỉ bằng {@code operationId} + input Map — mapping field
 * hoàn toàn theo khai báo YAML trong {@code mapping-definitions/}, không cần DTO hay
 * class Java riêng cho từng API.
 *
 * <p>Đây là điểm thay thế cho việc phải viết một {@code XxxMappingService} thủ công
 * (như {@code TransferMappingService}) cho mỗi API mới.</p>
 *
 * <pre>
 * Map&lt;String, Object&gt; input = Map.of(
 *     "debitAccount", "VND1217000011000",
 *     "creditAccount", "80000002233",
 *     "debitAmount", "98000"
 * );
 * Map&lt;String, Object&gt; result = declarativeBackendService.call("createMsbAccountTransfer", input, null);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeclarativeBackendService {

    private final ApiMappingRegistry mappingRegistry;
    private final DeclarativeMappingEngine mappingEngine;
    private final ResilientBackendCaller backendCaller;

    /**
     * Mapping input → gọi backend qua {@link ResilientBackendCaller} (đủ circuit breaker,
     * bulkhead, rate limiter, retry) → mapping response, tất cả theo khai báo YAML.
     *
     * @param operationId phải khớp với file mapping YAML VÀ route swagger đã import
     * @param input       dữ liệu đầu vào dạng Map lồng nhau (hỗ trợ dot-path trong mapping)
     * @param headers     header bổ sung gửi backend, có thể null
     * @throws QrException nếu không tìm thấy mapping definition cho operationId
     * @throws FieldMappingException nếu thiếu field bắt buộc ở chiều request hoặc response
     */
    public Map<String, Object> call(String operationId, Map<String, Object> input,
                                    Map<String, String> headers) {

        ApiMappingDefinition definition = mappingRegistry.find(operationId)
                .orElseThrow(() -> new QrException(
                        "Không tìm thấy mapping definition cho operationId=" + operationId
                                + ". Thêm file YAML vào mapping-definitions/ để đăng ký.",
                        HttpStatus.NOT_FOUND, "MAPPING_NOT_FOUND"));

        ObjectNode payload = mappingEngine.buildRequest(operationId, definition.getRequest(), input);

        log.info("Gọi backend qua declarative mapping: operationId={}", operationId);

        // route.timeoutMs (nếu khai báo trong file mapping cho backend không swagger)
        // ghi đè timeout đọc mặc định — dùng bản overload có connect/read timeout riêng.
        Integer readTimeoutMs = definition.getRoute() != null ? definition.getRoute().getTimeoutMs() : null;
        RestResponse<JsonNode> response = readTimeoutMs != null
                ? backendCaller.call(operationId, Map.of(), payload, headers, null, readTimeoutMs)
                : backendCaller.call(operationId, Map.of(), payload, headers);

        if (!response.isSuccess()) {
            log.warn("Backend trả lỗi cho operationId={}: {}", operationId, response.getErrorMessage());
            throw new QrException(
                    "Backend lỗi cho " + operationId + ": " + response.getErrorMessage(),
                    response.getStatusCode() != null
                            ? HttpStatus.valueOf(response.getStatusCode().value())
                            : HttpStatus.BAD_GATEWAY,
                    "BACKEND_CALL_FAILED");
        }

        return mappingEngine.buildResponse(operationId, definition.getResponse(), response.getBody());
    }
}
