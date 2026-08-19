package msb.com.vn.qrservice.mapping.declarative;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nạp mọi file {@code .esql} trong {@code transform-definitions/} khi khởi động, đăng ký theo
 * tên file (không phần mở rộng) làm {@code operationId}. Thêm transform mới = thêm 1 file,
 * không cần build lại code Java.
 *
 * <p>Cùng phong cách với {@code ApiMappingRegistry} và {@code SwaggerAutoLoader}: quét thư mục,
 * log rõ số file/lỗi, lỗi cú pháp ở một file không làm sập ứng dụng.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonToJsonTransformRegistry {

    private final JsonToJsonScriptParser scriptParser;

    private final java.util.Map<String, JsonToJsonTransformDefinition> definitions = new ConcurrentHashMap<>();

    @Value("${transform.definitions.path:classpath:transform-definitions/}")
    private String definitionsPath;

    @Value("${transform.definitions.auto-load:true}")
    private boolean autoLoad;

    @PostConstruct
    public void loadAll() {
        if (!autoLoad) {
            log.info("Transform definitions auto-load bị tắt (transform.definitions.auto-load=false)");
            return;
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  JSON-TO-JSON TRANSFORM REGISTRY: Scanning {}", definitionsPath);
        log.info("═══════════════════════════════════════════════════════════════");

        int loaded = 0;
        int failed = 0;

        try {
            for (Resource resource : scanTransformFiles()) {
                String filename = resource.getFilename();
                String operationId = extractOperationId(filename);
                try {
                    String content = new String(resource.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8);
                    List<JsonToJsonAssignment> assignments = scriptParser.parse(content);

                    definitions.put(operationId,
                            new JsonToJsonTransformDefinition(operationId, assignments, content));
                    loaded++;
                    log.info("  ✓ Loaded: {} → operationId={} ({} dòng gán)",
                            filename, operationId, assignments.size());

                } catch (JsonToJsonSyntaxException e) {
                    failed++;
                    log.error("  ✗ Failed: {} → {}", filename, e.getMessage());
                } catch (Exception e) {
                    failed++;
                    log.error("  ✗ Failed: {} → lỗi không mong đợi: {}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Lỗi scan thư mục transform-definitions: {}", e.getMessage(), e);
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  JSON-TO-JSON TRANSFORM REGISTRY: Hoàn tất — loaded={}, failed={}", loaded, failed);
        log.info("═══════════════════════════════════════════════════════════════");
    }

    public Optional<JsonToJsonTransformDefinition> find(String operationId) {
        return Optional.ofNullable(definitions.get(operationId));
    }

    public List<String> getRegisteredOperationIds() {
        return List.copyOf(definitions.keySet());
    }

    private Resource[] scanTransformFiles() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = definitionsPath + (definitionsPath.endsWith("/") ? "" : "/") + "*.esql";
        return resolver.getResources(pattern);
    }

    private String extractOperationId(String filename) {
        if (filename == null) {
            return "unknown";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
}
