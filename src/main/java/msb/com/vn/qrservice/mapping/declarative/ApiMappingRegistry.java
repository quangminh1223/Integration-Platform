package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.swagger.model.SwaggerRouteDefinition;
import msb.com.vn.qrservice.swagger.registry.DynamicRouteRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nạp mọi file YAML trong {@code mapping-definitions/} khi khởi động, đăng ký theo
 * {@code operationId}. Thêm API mới = thêm 1 file YAML, không cần build lại code Java.
 *
 * <p>Cùng phong cách với {@code SwaggerAutoLoader}: quét thư mục, log rõ số file/lỗi.</p>
 *
 * <h3>Backend không có swagger</h3>
 * <p>Nếu file mapping có khai báo {@code route:} ({@link BackendRouteDefinition}), registry
 * này tự dựng một {@link SwaggerRouteDefinition} tối giản (chỉ đủ field
 * {@code SwaggerBackendCaller} cần: method, path, targetBaseUrl, timeout) và đăng ký thẳng
 * vào {@link DynamicRouteRegistry} — cùng registry mà {@code SwaggerAutoLoader} dùng.
 * {@code ResilientBackendCaller} phía sau không phân biệt được route này khác gì route
 * parse từ file swagger thật, nên toàn bộ circuit breaker/bulkhead/retry/transaction log
 * vẫn hoạt động đầy đủ.</p>
 */
@Slf4j
@Component
public class ApiMappingRegistry {

    /** swaggerId giả dùng khi đăng ký route thủ công — để phân biệt trong log/registry */
    private static final String MANUAL_ROUTE_SWAGGER_ID = "manual-route";

    private final Map<String, ApiMappingDefinition> definitions = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final DynamicRouteRegistry routeRegistry;

    @Value("${mapping.definitions.path:classpath:mapping-definitions/}")
    private String definitionsPath;

    @Value("${mapping.definitions.auto-load:true}")
    private boolean autoLoad;

    public ApiMappingRegistry(DynamicRouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    @PostConstruct
    public void loadAll() {
        if (!autoLoad) {
            log.info("Mapping definitions auto-load bị tắt (mapping.definitions.auto-load=false)");
            return;
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  API MAPPING REGISTRY: Scanning {}", definitionsPath);
        log.info("═══════════════════════════════════════════════════════════════");

        int loaded = 0;
        int failed = 0;
        int manualRoutes = 0;

        try {
            for (Resource resource : scanMappingFiles()) {
                String filename = resource.getFilename();
                try {
                    ApiMappingDefinition definition = yamlMapper.readValue(
                            resource.getInputStream(), ApiMappingDefinition.class);

                    if (definition.getOperationId() == null || definition.getOperationId().isBlank()) {
                        log.error("  ✗ {} → thiếu 'operationId', bỏ qua file", filename);
                        failed++;
                        continue;
                    }

                    definitions.put(definition.getOperationId(), definition);

                    if (definition.getRoute() != null) {
                        registerManualRoute(definition);
                        manualRoutes++;
                    }

                    loaded++;
                    log.info("  ✓ Loaded: {} → operationId={} ({} request fields, {} response fields{})",
                            filename, definition.getOperationId(),
                            definition.getRequest().size(), definition.getResponse().size(),
                            definition.getRoute() != null ? ", route thủ công" : "");

                } catch (Exception e) {
                    failed++;
                    log.error("  ✗ Failed: {} → {}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Lỗi scan thư mục mapping-definitions: {}", e.getMessage(), e);
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  API MAPPING REGISTRY: Hoàn tất — loaded={}, failed={}, manualRoutes={}",
                loaded, failed, manualRoutes);
        log.info("═══════════════════════════════════════════════════════════════");
    }

    /**
     * Dựng {@link SwaggerRouteDefinition} tối giản từ {@link BackendRouteDefinition} và
     * đăng ký vào {@link DynamicRouteRegistry} — dùng khi backend không có swagger.
     */
    private void registerManualRoute(ApiMappingDefinition definition) {
        BackendRouteDefinition routeConfig = definition.getRoute();

        if (routeConfig.getBaseUrl() == null || routeConfig.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "route.baseUrl không được để trống cho operationId=" + definition.getOperationId());
        }
        if (routeConfig.getPath() == null || routeConfig.getPath().isBlank()) {
            throw new IllegalStateException(
                    "route.path không được để trống cho operationId=" + definition.getOperationId());
        }

        SwaggerRouteDefinition route = SwaggerRouteDefinition.builder()
                .operationId(definition.getOperationId())
                .method(HttpMethod.valueOf(routeConfig.getMethod().toUpperCase()))
                .path(routeConfig.getPath())
                .targetBaseUrl(routeConfig.getBaseUrl())
                .swaggerId(MANUAL_ROUTE_SWAGGER_ID)
                .summary(definition.getDescription())
                .build();

        routeRegistry.register(MANUAL_ROUTE_SWAGGER_ID, route);
        log.info("  → Đăng ký route thủ công: {} {}{} (operationId={})",
                routeConfig.getMethod(), routeConfig.getBaseUrl(), routeConfig.getPath(),
                definition.getOperationId());
    }

    /**
     * Đăng ký/cập nhật mapping ngay tại runtime — dùng cho hot-reload, hoặc test.
     */
    public void register(ApiMappingDefinition definition) {
        definitions.put(definition.getOperationId(), definition);
        if (definition.getRoute() != null) {
            registerManualRoute(definition);
        }
        log.info("Đã đăng ký mapping cho operationId={}", definition.getOperationId());
    }

    public Optional<ApiMappingDefinition> find(String operationId) {
        return Optional.ofNullable(definitions.get(operationId));
    }

    public List<String> getRegisteredOperationIds() {
        return List.copyOf(definitions.keySet());
    }

    private Resource[] scanMappingFiles() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = definitionsPath + (definitionsPath.endsWith("/") ? "" : "/") + "*.yml";
        Resource[] ymlFiles = resolver.getResources(pattern);

        String patternYaml = definitionsPath + (definitionsPath.endsWith("/") ? "" : "/") + "*.yaml";
        Resource[] yamlFiles;
        try {
            yamlFiles = resolver.getResources(patternYaml);
        } catch (IOException e) {
            yamlFiles = new Resource[0];
        }

        Resource[] combined = new Resource[ymlFiles.length + yamlFiles.length];
        System.arraycopy(ymlFiles, 0, combined, 0, ymlFiles.length);
        System.arraycopy(yamlFiles, 0, combined, ymlFiles.length, yamlFiles.length);
        return combined;
    }
}
