package msb.com.vn.qrservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.BackendErrorType;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.logging.ExceptionLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * Function 1: Gọi HTTP thuần — truyền URL trực tiếp.
 *
 * Dùng cho mọi trường hợp call HTTP đơn giản không liên quan đến swagger:
 * - Gọi API nội bộ
 * - Gọi webhook
 * - Gọi 3rd party API
 *
 * <h3>Ví dụ:</h3>
 * <pre>
 * // GET
 * RestResponse&lt;JsonNode&gt; res = httpClientService.get(
 *     "https://api.example.com/users/123", null, null
 * );
 *
 * // POST
 * RestResponse&lt;JsonNode&gt; res = httpClientService.post(
 *     "https://api.example.com/orders",
 *     Map.of("item", "QR001", "amount", 50000),
 *     Map.of("Authorization", "Bearer token"),
 *     null, null
 * );
 *
 * // PUT với timeout
 * RestResponse&lt;JsonNode&gt; res = httpClientService.put(
 *     "https://api.example.com/users/123",
 *     updateBody, null, 3000, 10000
 * );
 *
 * // DELETE
 * RestResponse&lt;Void&gt; res = httpClientService.delete(
 *     "https://api.example.com/orders/456", null
 * );
 *
 * // Call dynamic method
 * RestResponse&lt;JsonNode&gt; res = httpClientService.call(
 *     HttpMethod.PATCH, "https://api.example.com/users/123",
 *     patchBody, null, null, null, null, JsonNode.class
 * );
 * </pre>
 */
@Slf4j
@Service
public class HttpClientService {

    @Value("${rest-client.connect-timeout-ms:5000}")
    private int defaultConnectTimeout;

    @Value("${rest-client.read-timeout-ms:30000}")
    private int defaultReadTimeout;

    private final RestTemplateBuilder restTemplateBuilder;
    private final ExceptionLogService exceptionLogService;
    private volatile RestTemplate defaultRestTemplate;

    public HttpClientService(RestTemplateBuilder restTemplateBuilder,
                             ExceptionLogService exceptionLogService) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.exceptionLogService = exceptionLogService;
    }

    // ─── Shortcut methods ─────────────────────────────────────────────────────

    /** GET — truyền URL trực tiếp */
    public RestResponse<JsonNode> get(String url,
                                       Map<String, String> headers,
                                       Map<String, String> queryParams) {
        return call(HttpMethod.GET, url, null, headers, queryParams, null, null, JsonNode.class);
    }

    /** POST — truyền URL + body */
    public RestResponse<JsonNode> post(String url, Object body,
                                        Map<String, String> headers,
                                        Integer connectTimeoutMs,
                                        Integer readTimeoutMs) {
        return call(HttpMethod.POST, url, body, headers, null, connectTimeoutMs, readTimeoutMs, JsonNode.class);
    }

    /** PUT — truyền URL + body */
    public RestResponse<JsonNode> put(String url, Object body,
                                       Map<String, String> headers,
                                       Integer connectTimeoutMs,
                                       Integer readTimeoutMs) {
        return call(HttpMethod.PUT, url, body, headers, null, connectTimeoutMs, readTimeoutMs, JsonNode.class);
    }

    /** DELETE — truyền URL */
    public <T> RestResponse<T> delete(String url, Map<String, String> headers, Class<T> responseType) {
        return call(HttpMethod.DELETE, url, null, headers, null, null, null, responseType);
    }

    public RestResponse<JsonNode> delete(String url, Map<String, String> headers) {
        return delete(url, headers, JsonNode.class);
    }

    // ─── Core method ──────────────────────────────────────────────────────────

    /**
     * Gọi HTTP động — truyền URL trực tiếp, chọn method.
     *
     * @param method          HTTP method (GET, POST, PUT, PATCH, DELETE...)
     * @param url             URL đầy đủ (ví dụ: https://api.example.com/users/123)
     * @param body            request body (null cho GET/DELETE)
     * @param headers         headers bổ sung
     * @param queryParams     query parameters (tự append vào URL)
     * @param connectTimeoutMs  timeout kết nối (null = dùng default)
     * @param readTimeoutMs     timeout đọc response (null = dùng default)
     * @param responseType    kiểu response mong muốn
     */
    public <T> RestResponse<T> call(HttpMethod method, String url,
                                     Object body,
                                     Map<String, String> headers,
                                     Map<String, String> queryParams,
                                     Integer connectTimeoutMs,
                                     Integer readTimeoutMs,
                                     Class<T> responseType) {

        String resolvedUrl = appendQueryParams(url, queryParams);
        RestTemplate restTemplate = resolveRestTemplate(connectTimeoutMs, readTimeoutMs);
        HttpEntity<Object> httpEntity = buildHttpEntity(body, headers);

        log.debug("HTTP call: {} {}", method, resolvedUrl);

        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    resolvedUrl, method, httpEntity, responseType);

            log.debug("HTTP response: {} {} → {}", method, resolvedUrl, response.getStatusCode());
            return RestResponse.ok(response.getStatusCode(), response.getBody());

        } catch (HttpClientErrorException ex) {
            // 4xx — lỗi nghiệp vụ
            return handleException(method, resolvedUrl, BackendErrorType.BUSINESS,
                    "HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
                    ex, ex.getStatusCode());

        } catch (HttpServerErrorException ex) {
            // 5xx — lỗi hệ thống
            return handleException(method, resolvedUrl, BackendErrorType.SYSTEM,
                    "HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
                    ex, ex.getStatusCode());

        } catch (HttpStatusCodeException ex) {
            return handleException(method, resolvedUrl, BackendErrorType.UNKNOWN,
                    "HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
                    ex, ex.getStatusCode());

        } catch (ResourceAccessException ex) {
            BackendErrorType type = classifyResourceAccess(ex);
            return handleException(method, resolvedUrl, type, ex.getMessage(), ex, type.getHttpStatus());

        } catch (Exception ex) {
            return handleException(method, resolvedUrl, BackendErrorType.UNKNOWN,
                    ex.getMessage(), ex, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Xử lý exception tập trung: log async + trả RestResponse.error (KHÔNG throw).
     */
    private <T> RestResponse<T> handleException(HttpMethod method, String url,
                                                 BackendErrorType errorType, String message,
                                                 Throwable throwable, HttpStatusCode statusCode) {
        // Log async — không block luồng
        exceptionLogService.logException(method + " " + url, url, errorType, message, throwable);
        log.warn("HTTP call [{}] {} → {} ({})", method, url, errorType.getCode(), message);
        return RestResponse.error(statusCode != null ? statusCode : errorType.getHttpStatus(),
                "[" + errorType.getCode() + "] " + message);
    }

    private BackendErrorType classifyResourceAccess(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) return BackendErrorType.TIMEOUT;
        if (cause instanceof ConnectException) return BackendErrorType.CONNECTION;
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("timed out") || msg.contains("timeout")) return BackendErrorType.TIMEOUT;
        return BackendErrorType.CONNECTION;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private RestTemplate resolveRestTemplate(Integer connectMs, Integer readMs) {
        boolean custom = (connectMs != null || readMs != null);
        if (custom) {
            return restTemplateBuilder
                    .connectTimeout(Duration.ofMillis(connectMs != null ? connectMs : defaultConnectTimeout))
                    .readTimeout(Duration.ofMillis(readMs != null ? readMs : defaultReadTimeout))
                    .build();
        }
        if (defaultRestTemplate == null) {
            synchronized (this) {
                if (defaultRestTemplate == null) {
                    defaultRestTemplate = restTemplateBuilder
                            .connectTimeout(Duration.ofMillis(defaultConnectTimeout))
                            .readTimeout(Duration.ofMillis(defaultReadTimeout))
                            .build();
                }
            }
        }
        return defaultRestTemplate;
    }

    private String appendQueryParams(String url, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) return url;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        queryParams.forEach(builder::queryParam);
        return builder.build(false).toUriString();
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
}
