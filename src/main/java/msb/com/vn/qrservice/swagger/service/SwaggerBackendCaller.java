package msb.com.vn.qrservice.swagger.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.BackendErrorType;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.config.BackendEndpointConfig;
import msb.com.vn.qrservice.logging.ExceptionLogService;
import msb.com.vn.qrservice.logging.TransactionLogService;
import msb.com.vn.qrservice.swagger.model.SwaggerRouteDefinition;
import msb.com.vn.qrservice.swagger.registry.DynamicRouteRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * FUNCTION 2: Chuyên gọi RESTful API qua file Swagger.
 *
 * KHÁC với HttpClientService (Function 1 - gọi HTTP trực tiếp bằng URL):
 * - Function 2 KHÔNG truyền URL trực tiếp.
 * - Chỉ cần truyền operationId → tự lookup swagger → tự build URL/method → gọi.
 * - Tự execute HTTP độc lập (RestTemplate riêng), KHÔNG dùng chung với Function 1.
 * - Tự động ghi transaction log.
 * - Lấy domain từ BackendEndpointConfig.
 *
 * <h3>Ví dụ sử dụng:</h3>
 * <pre>
 * // Gọi bằng operationId
 * RestResponse&lt;JsonNode&gt; res = swaggerBackendCaller.call(
 *     "createMsbAccountTransfer",
 *     Map.of(),                   // path params (rỗng nếu không có)
 *     transferBody,               // body
 *     null                        // headers
 * );
 *
 * // Gọi bằng method + path
 * RestResponse&lt;JsonNode&gt; res = swaggerBackendCaller.callByPath(
 *     HttpMethod.POST,
 *     "/party/msb/transfer/accttrf/create",
 *     Map.of(), body, null
 * );
 * </pre>
 */
@Slf4j
@Service
public class SwaggerBackendCaller {

    private final DynamicRouteRegistry routeRegistry;
    private final BackendEndpointConfig endpointConfig;
    private final TransactionLogService transactionLogService;
    private final ExceptionLogService exceptionLogService;
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${spring.application.name:qr-service}")
    private String appName;

    @Value("${rest-client.connect-timeout-ms:5000}")
    private int defaultConnectTimeout;

    @Value("${rest-client.read-timeout-ms:30000}")
    private int defaultReadTimeout;

    /** RestTemplate riêng cho swagger calls — KHÔNG dùng chung với HttpClientService */
    private volatile RestTemplate swaggerRestTemplate;

    public SwaggerBackendCaller(DynamicRouteRegistry routeRegistry,
                                BackendEndpointConfig endpointConfig,
                                TransactionLogService transactionLogService,
                                ExceptionLogService exceptionLogService,
                                RestTemplateBuilder restTemplateBuilder) {
        this.routeRegistry = routeRegistry;
        this.endpointConfig = endpointConfig;
        this.transactionLogService = transactionLogService;
        this.exceptionLogService = exceptionLogService;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    // ─── Call by operationId ──────────────────────────────────────────────────

    public RestResponse<JsonNode> call(String operationId,
                                        Map<String, Object> pathParams,
                                        Object body,
                                        Map<String, String> extraHeaders) {
        return call(operationId, pathParams, body, extraHeaders, null, null, JsonNode.class);
    }

    public RestResponse<JsonNode> call(String operationId,
                                        Map<String, Object> pathParams,
                                        Object body,
                                        Map<String, String> extraHeaders,
                                        Integer connectTimeoutMs,
                                        Integer readTimeoutMs) {
        return call(operationId, pathParams, body, extraHeaders, connectTimeoutMs, readTimeoutMs, JsonNode.class);
    }

    public <T> RestResponse<T> call(String operationId,
                                     Map<String, Object> pathParams,
                                     Object body,
                                     Map<String, String> extraHeaders,
                                     Class<T> responseType) {
        return call(operationId, pathParams, body, extraHeaders, null, null, responseType);
    }

    /**
     * Gọi backend API qua swagger — full options.
     */
    public <T> RestResponse<T> call(String operationId,
                                     Map<String, Object> pathParams,
                                     Object body,
                                     Map<String, String> extraHeaders,
                                     Integer connectTimeoutMs,
                                     Integer readTimeoutMs,
                                     Class<T> responseType) {

        // 1. Lookup route theo operationId
        SwaggerRouteDefinition route = findByOperationId(operationId);

        // 2. Build URL từ swagger + config
        String targetUrl = buildTargetUrl(route, pathParams);

        // 3. Timeout: ưu tiên param → config swagger → default
        int connectMs = resolveTimeout(connectTimeoutMs, null, defaultConnectTimeout);
        int readMs = resolveTimeout(readTimeoutMs, endpointConfig.getTimeoutMs(route.getSwaggerId()), defaultReadTimeout);

        log.info("SwaggerBackendCaller: {} {} (operationId={})", route.getMethod(), targetUrl, operationId);

        // 4. Execute + Log
        LocalDateTime receivedAt = LocalDateTime.now();
        RestResponse<T> response = execute(route.getMethod(), targetUrl, body, extraHeaders,
                connectMs, readMs, responseType, operationId);
        LocalDateTime processedAt = LocalDateTime.now();

        logTransaction(operationId, targetUrl, body, response, receivedAt, processedAt);

        return response;
    }

    // ─── Call by method + path ────────────────────────────────────────────────

    public RestResponse<JsonNode> callByPath(HttpMethod method,
                                              String path,
                                              Map<String, Object> pathParams,
                                              Object body,
                                              Map<String, String> extraHeaders) {
        return callByPath(method, path, pathParams, body, extraHeaders, null, null, JsonNode.class);
    }

    public <T> RestResponse<T> callByPath(HttpMethod method,
                                           String path,
                                           Map<String, Object> pathParams,
                                           Object body,
                                           Map<String, String> extraHeaders,
                                           Integer connectTimeoutMs,
                                           Integer readTimeoutMs,
                                           Class<T> responseType) {

        Optional<SwaggerRouteDefinition> routeOpt = routeRegistry.find(method, path);
        if (routeOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Không tìm thấy route: " + method + " " + path
                    + ". Kiểm tra file swagger trong swagger-definitions/");
        }

        SwaggerRouteDefinition route = routeOpt.get();
        String targetUrl = buildTargetUrl(route, pathParams);

        int connectMs = resolveTimeout(connectTimeoutMs, null, defaultConnectTimeout);
        int readMs = resolveTimeout(readTimeoutMs, endpointConfig.getTimeoutMs(route.getSwaggerId()), defaultReadTimeout);

        log.info("SwaggerBackendCaller: {} {} (by path)", method, targetUrl);

        LocalDateTime receivedAt = LocalDateTime.now();
        RestResponse<T> response = execute(method, targetUrl, body, extraHeaders,
                connectMs, readMs, responseType, route.getOperationId());
        LocalDateTime processedAt = LocalDateTime.now();

        logTransaction(route.getOperationId(), targetUrl, body, response, receivedAt, processedAt);

        return response;
    }

    // ─── Liệt kê operations có sẵn ───────────────────────────────────────────

    public Map<String, String> listAvailableOperations() {
        Map<String, String> ops = new HashMap<>();
        routeRegistry.getAllRoutes().forEach(route ->
                ops.put(route.getOperationId(),
                        route.getMethod() + " " + route.getTargetBaseUrl() + route.getPath()
                        + (route.getSummary() != null ? " [" + route.getSummary() + "]" : ""))
        );
        return ops;
    }

    // ─── HTTP execution (riêng, không dùng chung Function 1) ─────────────────

    private <T> RestResponse<T> execute(HttpMethod method, String url, Object body,
                                         Map<String, String> extraHeaders,
                                         int connectMs, int readMs, Class<T> responseType,
                                         String operationId) {
        RestTemplate restTemplate = resolveRestTemplate(connectMs, readMs);
        HttpEntity<Object> httpEntity = buildHttpEntity(body, extraHeaders);

        try {
            ResponseEntity<T> resp = restTemplate.exchange(url, method, httpEntity, responseType);
            return RestResponse.ok(resp.getStatusCode(), resp.getBody());

        } catch (HttpClientErrorException ex) {
            // 4xx — lỗi nghiệp vụ, KHÔNG retry
            return handleException(operationId, url, BackendErrorType.BUSINESS,
                    "HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
                    ex, ex.getStatusCode());

        } catch (HttpServerErrorException ex) {
            // 5xx — lỗi hệ thống backend, có thể retry
            return handleException(operationId, url, BackendErrorType.SYSTEM,
                    "HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
                    ex, ex.getStatusCode());

        } catch (HttpStatusCodeException ex) {
            return handleException(operationId, url, BackendErrorType.UNKNOWN,
                    "HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
                    ex, ex.getStatusCode());

        } catch (ResourceAccessException ex) {
            // Timeout / Connection error
            BackendErrorType type = classifyResourceAccess(ex);
            return handleException(operationId, url, type, ex.getMessage(), ex, type.getHttpStatus());

        } catch (Exception ex) {
            // Lỗi không xác định (serialization, null pointer...)
            return handleException(operationId, url, BackendErrorType.UNKNOWN,
                    ex.getMessage(), ex, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Xử lý exception tập trung: log async + trả RestResponse.error (KHÔNG throw).
     * Đảm bảo luồng xử lý không bị crash, không bị block bởi I/O log.
     */
    private <T> RestResponse<T> handleException(String operationId, String url,
                                                 BackendErrorType errorType, String message,
                                                 Throwable throwable, HttpStatusCode statusCode) {
        // Log async — không block luồng chính
        exceptionLogService.logException(operationId, url, errorType, message, throwable);

        // Log ngắn gọn ra app log
        log.warn("Backend call [{}] {} → {} ({})", operationId, url, errorType.getCode(), message);

        return RestResponse.error(statusCode != null ? statusCode : errorType.getHttpStatus(),
                "[" + errorType.getCode() + "] " + message);
    }

    /**
     * Phân biệt timeout vs connection error.
     */
    private BackendErrorType classifyResourceAccess(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) {
            return BackendErrorType.TIMEOUT;
        }
        if (cause instanceof ConnectException) {
            return BackendErrorType.CONNECTION;
        }
        // ResourceAccessException thường là timeout hoặc connection
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("timed out") || msg.contains("timeout")) {
            return BackendErrorType.TIMEOUT;
        }
        return BackendErrorType.CONNECTION;
    }

    private RestTemplate resolveRestTemplate(int connectMs, int readMs) {
        // Nếu timeout khác default → tạo instance riêng
        if (connectMs != defaultConnectTimeout || readMs != defaultReadTimeout) {
            return restTemplateBuilder
                    .connectTimeout(Duration.ofMillis(connectMs))
                    .readTimeout(Duration.ofMillis(readMs))
                    .build();
        }
        if (swaggerRestTemplate == null) {
            synchronized (this) {
                if (swaggerRestTemplate == null) {
                    swaggerRestTemplate = restTemplateBuilder
                            .connectTimeout(Duration.ofMillis(defaultConnectTimeout))
                            .readTimeout(Duration.ofMillis(defaultReadTimeout))
                            .build();
                }
            }
        }
        return swaggerRestTemplate;
    }

    private HttpEntity<Object> buildHttpEntity(Object body, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        return new HttpEntity<>(body, headers);
    }

    private void logTransaction(String operationId, String url, Object body,
                                RestResponse<?> response,
                                LocalDateTime receivedAt, LocalDateTime processedAt) {
        transactionLogService.logTransaction(
                operationId,
                url,
                body,
                response.getBody(),
                response.isSuccess() ? appName + ".0" : String.valueOf(response.getStatusCode().value()),
                response.isSuccess() ? "Transaction is successfull!" : response.getErrorMessage(),
                response.isSuccess() ? "4" : "5",
                receivedAt,
                processedAt
        );
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private int resolveTimeout(Integer param, Integer configValue, int defaultValue) {
        if (param != null) return param;
        if (configValue != null) return configValue;
        return defaultValue;
    }

    private SwaggerRouteDefinition findByOperationId(String operationId) {
        return routeRegistry.getAllRoutes().stream()
                .filter(r -> operationId.equals(r.getOperationId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy operationId: '" + operationId + "'"
                        + ". Kiểm tra file swagger trong swagger-definitions/"
                        + ". Các operation có sẵn: " + listAvailableOperations().keySet()));
    }

    private String buildTargetUrl(SwaggerRouteDefinition route, Map<String, Object> pathParams) {
        // 1. Ưu tiên domain từ config
        String baseUrl = null;
        if (route.getSwaggerId() != null) {
            baseUrl = endpointConfig.getBaseUrl(route.getSwaggerId());
        }
        // 2. Fallback về targetBaseUrl từ swagger
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = route.getTargetBaseUrl() != null ? route.getTargetBaseUrl() : "";
        }

        String path = route.getPath();
        if (pathParams != null && !pathParams.isEmpty()) {
            for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }

        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl + path.substring(1);
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}
