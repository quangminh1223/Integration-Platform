package msb.com.vn.qrservice.mapping.declarative;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gọi bất kỳ API backend đã có file mapping YAML, chỉ bằng operationId — không cần
 * endpoint riêng, không cần DTO riêng cho từng API.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/declarative")
@RequiredArgsConstructor
@Tag(name = "Declarative Mapping", description = "Gọi backend qua mapping YAML, không cần DTO")
public class DeclarativeApiController {

    private final DeclarativeBackendService declarativeBackendService;
    private final ApiMappingRegistry mappingRegistry;

    @PostMapping("/{operationId}")
    @Operation(
        summary = "Gọi backend theo operationId, mapping field theo file YAML tương ứng",
        description = """
            Ví dụ: POST /api/v1/declarative/createMsbAccountTransfer
            Body: {"debitAccount":"...","creditAccount":"...","debitAmount":"98000",...}

            Field bắt buộc thiếu → HTTP 400 (lỗi caller).
            Backend trả về thiếu field hợp đồng → HTTP 502 (lỗi tích hợp).
            """
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> call(
            @PathVariable String operationId,
            @RequestBody Map<String, Object> input,
            @RequestHeader Map<String, String> headers) {

        log.info("Declarative call: operationId={}, fields={}", operationId, input.keySet());
        Map<String, Object> result = declarativeBackendService.call(operationId, input, headers);
        return ResponseEntity.ok(ApiResponse.success("Gọi " + operationId + " thành công", result));
    }

    @GetMapping("/mappings")
    @Operation(summary = "Danh sách operationId đã có mapping definition")
    public ResponseEntity<ApiResponse<List<String>>> listMappings() {
        return ResponseEntity.ok(ApiResponse.success(mappingRegistry.getRegisteredOperationIds()));
    }
}
