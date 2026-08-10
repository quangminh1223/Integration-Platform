package msb.com.vn.qrservice.swagger.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.swagger.model.SwaggerImportResult;
import msb.com.vn.qrservice.swagger.model.SwaggerRouteDefinition;
import msb.com.vn.qrservice.swagger.registry.DynamicRouteRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Parse file Swagger 2.0 / OpenAPI 3.x và đăng ký các route vào DynamicRouteRegistry.
 *
 * Hỗ trợ:
 * - Swagger 2.0 JSON / YAML
 * - OpenAPI 3.0 JSON / YAML
 * - Upload qua MultipartFile hoặc raw String content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwaggerImportService {

    private final DynamicRouteRegistry routeRegistry;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Import từ MultipartFile upload (JSON hoặc YAML).
     */
    public SwaggerImportResult importFromFile(MultipartFile file, String swaggerId) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String id = (swaggerId != null && !swaggerId.isBlank())
                    ? swaggerId
                    : (file.getOriginalFilename() != null ? file.getOriginalFilename() : "swagger");
            log.info("Importing swagger from file: {}, size={} bytes", file.getOriginalFilename(), file.getSize());
            return importFromContent(content, id);
        } catch (Exception e) {
            throw new IllegalArgumentException("Không thể đọc file swagger: " + e.getMessage(), e);
        }
    }

    /**
     * Import từ raw String content (JSON hoặc YAML).
     */
    public SwaggerImportResult importFromContent(String content, String swaggerId) {
        List<String> warnings = new ArrayList<>();

        // Parse swagger content
        OpenAPI openAPI = parseSwagger(content, warnings);

        // Extract metadata
        String title   = openAPI.getInfo() != null ? openAPI.getInfo().getTitle()   : "Unknown";
        String version = openAPI.getInfo() != null ? openAPI.getInfo().getVersion() : "1.0";
        String baseUrl = extractBaseUrl(openAPI);

        log.info("Parsed swagger: title={}, version={}, baseUrl={}", title, version, baseUrl);

        // Extract routes từ paths
        List<SwaggerRouteDefinition> routes = extractRoutes(openAPI, baseUrl, warnings);

        // Set swaggerId cho mỗi route (dùng để lookup domain từ config)
        routes.forEach(r -> r.setSwaggerId(swaggerId));

        // Đăng ký vào registry
        routeRegistry.registerAll(swaggerId, routes);

        // Build result
        List<SwaggerImportResult.RouteInfo> routeInfos = routes.stream()
                .map(r -> SwaggerImportResult.RouteInfo.builder()
                        .method(r.getMethod().name())
                        .path(r.getPath())
                        .operationId(r.getOperationId())
                        .summary(r.getSummary())
                        .registeredEndpoint("/api/v1/dynamic" + r.getPath())
                        .build())
                .toList();

        return SwaggerImportResult.builder()
                .swaggerId(swaggerId)
                .title(title)
                .version(version)
                .targetBaseUrl(baseUrl)
                .totalRoutes(routes.size())
                .routes(routeInfos)
                .importedAt(LocalDateTime.now())
                .warnings(warnings)
                .build();
    }

    // ─── Parse swagger ────────────────────────────────────────────────────────

    private OpenAPI parseSwagger(String content, List<String> warnings) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);       // resolve $ref
        options.setResolveFully(true);  // resolve fully nested $ref

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, options);

        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            warnings.addAll(result.getMessages());
            log.warn("Swagger parse warnings: {}", result.getMessages());
        }

        if (result.getOpenAPI() == null) {
            throw new IllegalArgumentException(
                    "File swagger không hợp lệ hoặc không thể parse. Warnings: " + warnings);
        }

        return result.getOpenAPI();
    }

    // ─── Extract base URL ─────────────────────────────────────────────────────

    private String extractBaseUrl(OpenAPI openAPI) {
        // OpenAPI 3.x: servers[0].url
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            String url = openAPI.getServers().get(0).getUrl();
            if (url != null && !url.equals("/")) {
                return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            }
        }
        // Swagger 2.0 extensions (swagger-parser convert sang OpenAPI 3 nên check extensions)
        if (openAPI.getExtensions() != null) {
            Object host     = openAPI.getExtensions().get("x-original-host");
            Object basePath = openAPI.getExtensions().get("x-original-basePath");
            if (host != null) {
                return "http://" + host + (basePath != null ? basePath : "");
            }
        }
        return "";
    }

    // ─── Extract routes ───────────────────────────────────────────────────────

    private List<SwaggerRouteDefinition> extractRoutes(OpenAPI openAPI, String baseUrl,
                                                        List<String> warnings) {
        List<SwaggerRouteDefinition> routes = new ArrayList<>();

        if (openAPI.getPaths() == null) {
            warnings.add("Swagger không có paths nào được định nghĩa");
            return routes;
        }

        openAPI.getPaths().forEach((path, pathItem) -> {
            extractOperations(pathItem).forEach((method, operation) -> {
                try {
                    SwaggerRouteDefinition route = buildRoute(path, method, operation,
                            pathItem, baseUrl, openAPI);
                    routes.add(route);
                } catch (Exception e) {
                    warnings.add("Bỏ qua route " + method + " " + path + ": " + e.getMessage());
                    log.warn("Bỏ qua route {} {}: {}", method, path, e.getMessage());
                }
            });
        });

        return routes;
    }

    /**
     * Lấy tất cả operations từ PathItem theo method.
     */
    private Map<HttpMethod, Operation> extractOperations(PathItem pathItem) {
        Map<HttpMethod, Operation> ops = new LinkedHashMap<>();
        if (pathItem.getGet()    != null) ops.put(HttpMethod.GET,    pathItem.getGet());
        if (pathItem.getPost()   != null) ops.put(HttpMethod.POST,   pathItem.getPost());
        if (pathItem.getPut()    != null) ops.put(HttpMethod.PUT,    pathItem.getPut());
        if (pathItem.getPatch()  != null) ops.put(HttpMethod.PATCH,  pathItem.getPatch());
        if (pathItem.getDelete() != null) ops.put(HttpMethod.DELETE, pathItem.getDelete());
        if (pathItem.getHead()   != null) ops.put(HttpMethod.HEAD,   pathItem.getHead());
        if (pathItem.getOptions()!= null) ops.put(HttpMethod.OPTIONS,pathItem.getOptions());
        return ops;
    }

    private SwaggerRouteDefinition buildRoute(String path, HttpMethod method,
                                               Operation operation, PathItem pathItem,
                                               String baseUrl, OpenAPI openAPI) {
        // Parameters
        List<SwaggerRouteDefinition.ParameterDefinition> params = new ArrayList<>();

        // Path-level parameters
        if (pathItem.getParameters() != null) {
            pathItem.getParameters().forEach(p -> params.add(toParamDef(p)));
        }
        // Operation-level parameters (override path-level)
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(p -> params.add(toParamDef(p)));
        }

        // Request body schema
        JsonNode requestBodySchema = extractRequestBodySchema(operation.getRequestBody());

        // Response schemas
        Map<String, JsonNode> responseSchemas = extractResponseSchemas(operation.getResponses());

        // Consumes / Produces
        List<String> consumes = extractConsumes(operation.getRequestBody());
        List<String> produces = extractProduces(operation.getResponses());

        return SwaggerRouteDefinition.builder()
                .path(path)
                .method(method)
                .operationId(operation.getOperationId() != null
                        ? operation.getOperationId()
                        : method.name().toLowerCase() + path.replace("/", "_"))
                .summary(operation.getSummary())
                .description(operation.getDescription())
                .tags(operation.getTags())
                .parameters(params)
                .requestBodySchema(requestBodySchema)
                .responseSchemas(responseSchemas)
                .consumes(consumes)
                .produces(produces)
                .targetBaseUrl(baseUrl)
                .swaggerId(null) // sẽ được set bởi caller (importFromContent)
                .build();
    }

    private SwaggerRouteDefinition.ParameterDefinition toParamDef(Parameter p) {
        return SwaggerRouteDefinition.ParameterDefinition.builder()
                .name(p.getName())
                .in(p.getIn())
                .description(p.getDescription())
                .required(Boolean.TRUE.equals(p.getRequired()))
                .schema(schemaToJsonNode(p.getSchema()))
                .build();
    }

    private JsonNode extractRequestBodySchema(RequestBody requestBody) {
        if (requestBody == null || requestBody.getContent() == null) return null;
        return requestBody.getContent().values().stream()
                .filter(mt -> mt.getSchema() != null)
                .map(mt -> schemaToJsonNode(mt.getSchema()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, JsonNode> extractResponseSchemas(
            io.swagger.v3.oas.models.responses.ApiResponses responses) {
        if (responses == null) return Map.of();
        Map<String, JsonNode> result = new LinkedHashMap<>();
        responses.forEach((statusCode, apiResponse) -> {
            JsonNode schema = extractResponseSchema(apiResponse);
            if (schema != null) result.put(statusCode, schema);
        });
        return result;
    }

    private JsonNode extractResponseSchema(ApiResponse apiResponse) {
        if (apiResponse == null || apiResponse.getContent() == null) return null;
        return apiResponse.getContent().values().stream()
                .filter(mt -> mt.getSchema() != null)
                .map(mt -> schemaToJsonNode(mt.getSchema()))
                .findFirst()
                .orElse(null);
    }

    private List<String> extractConsumes(RequestBody requestBody) {
        if (requestBody == null || requestBody.getContent() == null) {
            return List.of("application/json");
        }
        return new ArrayList<>(requestBody.getContent().keySet());
    }

    private List<String> extractProduces(io.swagger.v3.oas.models.responses.ApiResponses responses) {
        if (responses == null) return List.of("application/json");
        return responses.values().stream()
                .filter(r -> r.getContent() != null)
                .flatMap(r -> r.getContent().keySet().stream())
                .distinct()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private JsonNode schemaToJsonNode(Schema<?> schema) {
        if (schema == null) return null;
        try {
            return jsonMapper.valueToTree(schema);
        } catch (Exception e) {
            return null;
        }
    }
}
