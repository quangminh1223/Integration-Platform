package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.QrException;
import msb.com.vn.qrservice.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Chạy transform JSON→JSON theo file {@code .esql} đã đăng ký — dùng để test độc lập,
 * không cần gọi backend thật. Body gửi lên đóng vai {@code InputRoot.JSON.Data}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transform")
@RequiredArgsConstructor
@Tag(name = "JSON-to-JSON Transform", description = "Chạy transform theo file .esql, không cần DTO")
public class JsonToJsonTransformController {

    private final JsonToJsonTransformRegistry transformRegistry;
    private final JsonToJsonTransformEngine transformEngine;

    @PostMapping("/{operationId}")
    @Operation(
        summary = "Chạy transform theo operationId (tên file .esql, không đuôi mở rộng)",
        description = """
            Ví dụ: POST /api/v1/transform/chargeCollection
            Body chính là InputRoot.JSON.Data — không cần bọc thêm lớp nào.

            Field {@code required} thiếu → HTTP 400.
            """
    )
    public ResponseEntity<ApiResponse<JsonNode>> run(
            @PathVariable String operationId,
            @RequestBody JsonNode input) {

        JsonToJsonTransformDefinition definition = transformRegistry.find(operationId)
                .orElseThrow(() -> new QrException(
                        "Không tìm thấy transform definition cho operationId=" + operationId
                                + ". Thêm file " + operationId + ".esql vào transform-definitions/.",
                        HttpStatus.NOT_FOUND, "TRANSFORM_NOT_FOUND"));

        log.info("Chạy transform: operationId={}", operationId);
        ObjectNode output = transformEngine.execute(definition, input);

        return ResponseEntity.ok(ApiResponse.success("Transform " + operationId + " thành công", output));
    }

    @GetMapping("/definitions")
    @Operation(summary = "Danh sách operationId đã có transform definition")
    public ResponseEntity<ApiResponse<List<String>>> listDefinitions() {
        return ResponseEntity.ok(ApiResponse.success(transformRegistry.getRegisteredOperationIds()));
    }
}
