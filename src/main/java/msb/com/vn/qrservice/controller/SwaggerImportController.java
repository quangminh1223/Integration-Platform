package msb.com.vn.qrservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.swagger.model.SwaggerImportResult;
import msb.com.vn.qrservice.swagger.registry.DynamicRouteRegistry;
import msb.com.vn.qrservice.swagger.service.SwaggerImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/swagger")
@RequiredArgsConstructor
@Tag(name = "Swagger Import", description = "Import file Swagger 2.0 / OpenAPI 3.x để đăng ký route động")
public class SwaggerImportController {

    private final SwaggerImportService swaggerImportService;
    private final DynamicRouteRegistry routeRegistry;

    // ── 1. Upload file (multipart) ────────────────────────────────────────────

    @PostMapping(value = "/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Import swagger từ file upload",
        description = """
            Upload file Swagger 2.0 hoặc OpenAPI 3.x (JSON hoặc YAML).
            Sau khi import, các route trong swagger sẽ được đăng ký tự động.
            
            **Cách dùng với curl:**
            ```
            curl -X POST http://localhost:8080/api/v1/swagger/import/file \\
              -F "file=@/path/to/swagger.json" \\
              -F "swaggerId=my-service"
            ```
            """
    )
    public ResponseEntity<ApiResponse<SwaggerImportResult>> importFromFile(
            @Parameter(description = "File swagger JSON hoặc YAML", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "ID định danh swagger (dùng để update/xóa sau này)")
            @RequestParam(value = "swaggerId", required = false) String swaggerId) {

        // Nếu không truyền swaggerId thì dùng tên file
        String id = (swaggerId != null && !swaggerId.isBlank())
                ? swaggerId
                : file.getOriginalFilename();

        log.info("Import swagger file: {}, swaggerId={}", file.getOriginalFilename(), id);
        SwaggerImportResult result = swaggerImportService.importFromFile(file, id);

        return ResponseEntity.ok(ApiResponse.success(
                "Import thành công " + result.getTotalRoutes() + " routes", result));
    }

    // ── 2. Paste raw content (JSON/YAML string) ───────────────────────────────

    @PostMapping("/import/content")
    @Operation(
        summary = "Import swagger từ raw content (JSON hoặc YAML string)",
        description = """
            Paste trực tiếp nội dung file swagger vào body.
            
            **Cách dùng với curl:**
            ```
            curl -X POST http://localhost:8080/api/v1/swagger/import/content?swaggerId=my-service \\
              -H "Content-Type: text/plain" \\
              --data-binary @swagger.yaml
            ```
            """
    )
    public ResponseEntity<ApiResponse<SwaggerImportResult>> importFromContent(
            @Parameter(description = "Nội dung swagger JSON hoặc YAML")
            @RequestBody String content,

            @Parameter(description = "ID định danh swagger", required = true)
            @RequestParam String swaggerId) {

        log.info("Import swagger content, swaggerId={}, length={}", swaggerId, content.length());
        SwaggerImportResult result = swaggerImportService.importFromContent(content, swaggerId);

        return ResponseEntity.ok(ApiResponse.success(
                "Import thành công " + result.getTotalRoutes() + " routes", result));
    }

    // ── 3. Xem danh sách routes đã đăng ký ───────────────────────────────────

    @GetMapping("/routes")
    @Operation(summary = "Xem tất cả routes đã được đăng ký từ swagger")
    public ResponseEntity<ApiResponse<List<String>>> listRoutes() {
        List<String> routes = routeRegistry.getAllRoutes().stream()
                .map(r -> r.getMethod().name() + " " + r.getPath()
                        + " → /api/v1/dynamic" + r.getPath()
                        + (r.getSummary() != null ? " [" + r.getSummary() + "]" : ""))
                .sorted()
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                "Tổng " + routes.size() + " routes đang active", routes));
    }

    // ── 4. Xóa routes theo swaggerId ─────────────────────────────────────────

    @DeleteMapping("/routes/{swaggerId}")
    @Operation(summary = "Xóa tất cả routes của một swagger đã import")
    public ResponseEntity<ApiResponse<Void>> deleteRoutes(
            @PathVariable String swaggerId) {

        routeRegistry.deregisterBySwaggerId(swaggerId);
        log.info("Deleted routes for swaggerId={}", swaggerId);

        return ResponseEntity.ok(ApiResponse.success("Đã xóa routes của: " + swaggerId, null));
    }
}
