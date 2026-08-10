package msb.com.vn.qrservice.swagger.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.swagger.model.SwaggerImportResult;
import msb.com.vn.qrservice.swagger.service.SwaggerImportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tự động scan thư mục swagger-definitions/ khi app khởi động,
 * load tất cả file .json / .yaml / .yml và đăng ký route.
 *
 * Chỉ cần bỏ file swagger vào thư mục → restart app → endpoint sẵn sàng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SwaggerAutoLoader {

    private final SwaggerImportService swaggerImportService;

    @Value("${swagger.definitions.path:classpath:swagger-definitions/}")
    private String definitionsPath;

    @Value("${swagger.definitions.auto-load:true}")
    private boolean autoLoad;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoLoad) {
            log.info("Swagger auto-load bị tắt (swagger.definitions.auto-load=false)");
            return;
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  SWAGGER AUTO-LOADER: Scanning {}", definitionsPath);
        log.info("═══════════════════════════════════════════════════════════════");

        List<SwaggerImportResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            Resource[] resources = scanSwaggerFiles();

            if (resources.length == 0) {
                log.warn("Không tìm thấy file swagger nào trong: {}", definitionsPath);
                return;
            }

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                try {
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    // swaggerId = tên file không có extension
                    String swaggerId = extractSwaggerId(filename);

                    SwaggerImportResult result = swaggerImportService.importFromContent(content, swaggerId);
                    results.add(result);

                    log.info("  ✓ Loaded: {} → {} routes (target: {})",
                            filename, result.getTotalRoutes(), result.getTargetBaseUrl());

                } catch (Exception e) {
                    errors.add(filename + ": " + e.getMessage());
                    log.error("  ✗ Failed: {} → {}", filename, e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Lỗi scan thư mục swagger-definitions: {}", e.getMessage(), e);
        }

        // Summary
        int totalRoutes = results.stream().mapToInt(SwaggerImportResult::getTotalRoutes).sum();
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  SWAGGER AUTO-LOADER: Hoàn tất");
        log.info("  Files loaded  : {}/{}", results.size(), results.size() + errors.size());
        log.info("  Total routes  : {}", totalRoutes);
        if (!errors.isEmpty()) {
            log.info("  Errors        : {}", errors.size());
            errors.forEach(e -> log.info("    - {}", e));
        }
        log.info("═══════════════════════════════════════════════════════════════");
    }

    /**
     * Scan tất cả file .json, .yaml, .yml trong thư mục swagger-definitions.
     */
    private Resource[] scanSwaggerFiles() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // Scan cả 3 extension
        List<Resource> allResources = new ArrayList<>();
        for (String ext : new String[]{"*.json", "*.yaml", "*.yml"}) {
            try {
                String pattern = definitionsPath + (definitionsPath.endsWith("/") ? "" : "/") + ext;
                Resource[] found = resolver.getResources(pattern);
                for (Resource r : found) {
                    if (r.exists() && r.isReadable()) {
                        allResources.add(r);
                    }
                }
            } catch (IOException ignored) {
                // Không có file với extension này → bỏ qua
            }
        }

        return allResources.toArray(new Resource[0]);
    }

    /**
     * Lấy swaggerId từ tên file: "updateMsbCustomer-v1.0.0-swagger.json" → "updateMsbCustomer-v1.0.0-swagger"
     */
    private String extractSwaggerId(String filename) {
        if (filename == null) return "unknown";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
}
