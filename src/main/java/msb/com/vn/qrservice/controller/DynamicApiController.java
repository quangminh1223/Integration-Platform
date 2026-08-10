package msb.com.vn.qrservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.service.HttpClientService;
import msb.com.vn.qrservice.swagger.model.SwaggerRouteDefinition;
import msb.com.vn.qrservice.swagger.registry.DynamicRouteRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Dynamic API Gateway Controller.
 *
 * Catch-all endpoint: /api/v1/dynamic/**
 * Khi nhận request → lookup DynamicRouteRegistry → dùng HttpClientService forward tới backend target.
 *
 * Flow:
 * 1. Client gọi: POST /api/v1/dynamic/product/msb/customer/maintenance/amend/123
 * 2. Controller strip prefix "/api/v1/dynamic" → path = "/product/msb/customer/maintenance/amend/123"
 * 3. Lookup registry: tìm route PUT /product/msb/customer/maintenance/amend/{id}
 * 4. Build target URL: http://api.server.com/api/v1.0.0/product/msb/customer/maintenance/amend/123
 * 5. Forward request bằng HttpClientService (Function 1 - gọi HTTP trực tiếp)
 * 6. Trả response về client
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dynamic")
@RequiredArgsConstructor
@Tag(name = "Dynamic API Gateway", description = "Forward request tới backend dựa trên swagger đã import")
public class DynamicApiController {

    private final DynamicRouteRegistry routeRegistry;
    private final HttpClientService httpClientService;

    private static final String PATH_PREFIX = "/api/v1/dynamic";

    // ─── Catch-all cho mọi method ────────────────────────────────────────────

    @RequestMapping(value = "/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE,
            RequestMethod.HEAD, RequestMethod.OPTIONS
    })
    @Operation(
        summary = "Forward request tới backend theo swagger đã import",
        description = """
            Endpoint catch-all: mọi request tới /api/v1/dynamic/** sẽ được lookup
            trong DynamicRouteRegistry và forward tới backend target.
            """
    )
    public ResponseEntity<ApiResponse<Object>> handleDynamicRequest(
            HttpServletRequest servletRequest,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader Map<String, String> headers) {

        // 1. Extract path và method
        String fullPath = servletRequest.getRequestURI();
        String relativePath = extractRelativePath(fullPath);
        HttpMethod method = HttpMethod.valueOf(servletRequest.getMethod());

        log.info("Dynamic request: {} {} → lookup route...", method, relativePath);

        // 2. Lookup route trong registry
        Optional<SwaggerRouteDefinition> routeOpt = routeRegistry.find(method, relativePath);

        if (routeOpt.isEmpty()) {
            log.warn("Route không tìm thấy: {} {}", method, relativePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("ROUTE_NOT_FOUND",
                            "Không tìm thấy route: " + method + " " + relativePath
                            + ". Hãy import file swagger chứa endpoint này."));
        }

        SwaggerRouteDefinition route = routeOpt.get();

        // 3. Build target URL
        String targetUrl = buildTargetUrl(route, relativePath, servletRequest);
        log.info("Forwarding: {} {} → {}", method, relativePath, targetUrl);

        // 4. Build headers (loại bỏ host, content-length — để RestTemplate tự set)
        Map<String, String> forwardHeaders = buildForwardHeaders(headers);

        // 5. Extract query params
        Map<String, String> queryParams = extractQueryParams(servletRequest);

        // 6. Forward bằng HttpClientService (Function 1 - gọi HTTP trực tiếp bằng URL)
        RestResponse<JsonNode> response = httpClientService.call(
                method, targetUrl, body, forwardHeaders, queryParams,
                null, null, JsonNode.class);

        // 7. Trả response
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Forward thành công tới: " + targetUrl, response.getBody()));
        } else {
            return ResponseEntity.status(response.getStatusCode())
                    .body(ApiResponse.error("BACKEND_ERROR", response.getErrorMessage()));
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Strip prefix /api/v1/dynamic khỏi URI.
     * /api/v1/dynamic/product/msb/customer → /product/msb/customer
     */
    private String extractRelativePath(String fullPath) {
        // context-path = /api, nên URI thực tế bắt đầu bằng /api/v1/dynamic
        String prefix = "/v1/dynamic";
        int idx = fullPath.indexOf(prefix);
        if (idx >= 0) {
            String relative = fullPath.substring(idx + prefix.length());
            return relative.isEmpty() ? "/" : relative;
        }
        return fullPath;
    }

    /**
     * Build target URL từ route definition + request path thực tế.
     *
     * Route: targetBaseUrl = "http://api.server.com/api/v1.0.0"
     * Request path: /product/msb/customer/maintenance/amend/123
     * → http://api.server.com/api/v1.0.0/product/msb/customer/maintenance/amend/123
     */
    private String buildTargetUrl(SwaggerRouteDefinition route, String relativePath,
                                   HttpServletRequest request) {
        String baseUrl = route.getTargetBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            // Fallback: dùng header X-Target-Url nếu swagger không có host
            baseUrl = request.getHeader("X-Target-Url");
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://localhost:8080";
            }
        }

        // Đảm bảo không có double slash
        if (baseUrl.endsWith("/") && relativePath.startsWith("/")) {
            return baseUrl + relativePath.substring(1);
        }
        if (!baseUrl.endsWith("/") && !relativePath.startsWith("/")) {
            return baseUrl + "/" + relativePath;
        }
        return baseUrl + relativePath;
    }

    /**
     * Loại bỏ các header không nên forward (host, content-length, connection...).
     */
    private Map<String, String> buildForwardHeaders(Map<String, String> originalHeaders) {
        Map<String, String> forwarded = new HashMap<>();
        Set<String> skipHeaders = Set.of(
                "host", "content-length", "connection", "transfer-encoding",
                "accept-encoding", "upgrade", "keep-alive"
        );

        originalHeaders.forEach((key, value) -> {
            if (!skipHeaders.contains(key.toLowerCase())) {
                forwarded.put(key, value);
            }
        });

        return forwarded;
    }

    /**
     * Extract query parameters từ request.
     */
    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}
