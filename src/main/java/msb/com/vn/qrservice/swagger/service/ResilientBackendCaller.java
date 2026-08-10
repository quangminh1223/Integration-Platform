package msb.com.vn.qrservice.swagger.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.http.RestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Wrapper trên SwaggerBackendCaller với đầy đủ protection chống cascading failure.
 *
 * Thứ tự áp dụng (ngoài → trong):
 * 1. Rate Limiter  → kiểm soát TPS gửi sang backend (500 req/s)
 * 2. Bulkhead      → giới hạn concurrent calls (100 đồng thời)
 * 3. Circuit Breaker → ngắt mạch khi backend lỗi > 60%
 * 4. Retry         → retry 3 lần với exponential backoff
 * 5. Timeout       → kill call sau 12s
 *
 * Khi backend timeout/chậm:
 * - Bulkhead chỉ cho 100 threads chờ → 100 threads còn lại phục vụ request khác
 * - Circuit Breaker mở → trả lỗi ngay (0ms) thay vì chờ 10s timeout
 * - Rate Limiter giảm áp lực lên backend khi nó đang quá tải
 *
 * <h3>Sử dụng:</h3>
 * <pre>
 * // Thay SwaggerBackendCaller bằng ResilientBackendCaller
 * RestResponse&lt;JsonNode&gt; res = resilientBackendCaller.call(
 *     "updateMsbCustomer",
 *     Map.of("id", "123"),
 *     requestBody,
 *     null
 * );
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientBackendCaller {

    private final SwaggerBackendCaller swaggerBackendCaller;

    // ─── Call với đầy đủ protection ───────────────────────────────────────────

    /**
     * Gọi backend với Circuit Breaker + Bulkhead + Rate Limiter + Retry.
     * Nếu backend chết → trả fallback ngay, không block thread.
     */
    @CircuitBreaker(name = "backend-caller", fallbackMethod = "fallback")
    @Bulkhead(name = "backend-caller", fallbackMethod = "bulkheadFallback")
    @RateLimiter(name = "backend-caller", fallbackMethod = "rateLimitFallback")
    @Retry(name = "backend-caller")
    public RestResponse<JsonNode> call(String operationId,
                                        Map<String, Object> pathParams,
                                        Object body,
                                        Map<String, String> headers) {

        return swaggerBackendCaller.call(operationId, pathParams, body, headers);
    }

    /**
     * Gọi backend với custom timeout + protection.
     */
    @CircuitBreaker(name = "backend-caller", fallbackMethod = "fallback")
    @Bulkhead(name = "backend-caller", fallbackMethod = "bulkheadFallback")
    @RateLimiter(name = "backend-caller", fallbackMethod = "rateLimitFallback")
    @Retry(name = "backend-caller")
    public RestResponse<JsonNode> call(String operationId,
                                        Map<String, Object> pathParams,
                                        Object body,
                                        Map<String, String> headers,
                                        Integer connectTimeoutMs,
                                        Integer readTimeoutMs) {

        return swaggerBackendCaller.call(operationId, pathParams, body, headers,
                connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Gọi backend với response type cụ thể + protection.
     */
    @CircuitBreaker(name = "backend-caller", fallbackMethod = "fallbackTyped")
    @Bulkhead(name = "backend-caller", fallbackMethod = "bulkheadFallbackTyped")
    @RateLimiter(name = "backend-caller", fallbackMethod = "rateLimitFallbackTyped")
    @Retry(name = "backend-caller")
    public <T> RestResponse<T> call(String operationId,
                                     Map<String, Object> pathParams,
                                     Object body,
                                     Map<String, String> headers,
                                     Class<T> responseType) {

        return swaggerBackendCaller.call(operationId, pathParams, body, headers, responseType);
    }

    // ─── Fallback methods ─────────────────────────────────────────────────────

    /**
     * Fallback khi Circuit Breaker OPEN (backend đang chết).
     * Trả lỗi ngay lập tức — không chờ, không block thread.
     */
    private RestResponse<JsonNode> fallback(String operationId,
                                             Map<String, Object> pathParams,
                                             Object body,
                                             Map<String, String> headers,
                                             Throwable ex) {
        log.warn("CIRCUIT BREAKER OPEN: operationId={}, error={}", operationId, ex.getMessage());

        String message = (ex instanceof CallNotPermittedException)
                ? "Backend đang quá tải, circuit breaker đã mở. Vui lòng thử lại sau 30s."
                : "Backend không phản hồi: " + ex.getMessage();

        return RestResponse.error(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private RestResponse<JsonNode> fallback(String operationId,
                                             Map<String, Object> pathParams,
                                             Object body,
                                             Map<String, String> headers,
                                             Integer connectTimeoutMs,
                                             Integer readTimeoutMs,
                                             Throwable ex) {
        return fallback(operationId, pathParams, body, headers, ex);
    }

    private <T> RestResponse<T> fallbackTyped(String operationId,
                                               Map<String, Object> pathParams,
                                               Object body,
                                               Map<String, String> headers,
                                               Class<T> responseType,
                                               Throwable ex) {
        log.warn("CIRCUIT BREAKER OPEN: operationId={}, error={}", operationId, ex.getMessage());
        return RestResponse.error(HttpStatus.SERVICE_UNAVAILABLE,
                "Backend đang quá tải: " + ex.getMessage());
    }

    /**
     * Fallback khi Bulkhead đầy (quá nhiều concurrent calls).
     */
    private RestResponse<JsonNode> bulkheadFallback(String operationId,
                                                     Map<String, Object> pathParams,
                                                     Object body,
                                                     Map<String, String> headers,
                                                     Throwable ex) {
        log.warn("BULKHEAD FULL: operationId={}, concurrent calls đạt giới hạn", operationId);
        return RestResponse.error(HttpStatus.TOO_MANY_REQUESTS,
                "Hệ thống đang xử lý quá nhiều request tới backend. Vui lòng thử lại.");
    }

    private RestResponse<JsonNode> bulkheadFallback(String operationId,
                                                     Map<String, Object> pathParams,
                                                     Object body,
                                                     Map<String, String> headers,
                                                     Integer connectTimeoutMs,
                                                     Integer readTimeoutMs,
                                                     Throwable ex) {
        return bulkheadFallback(operationId, pathParams, body, headers, ex);
    }

    private <T> RestResponse<T> bulkheadFallbackTyped(String operationId,
                                                       Map<String, Object> pathParams,
                                                       Object body,
                                                       Map<String, String> headers,
                                                       Class<T> responseType,
                                                       Throwable ex) {
        log.warn("BULKHEAD FULL: operationId={}", operationId);
        return RestResponse.error(HttpStatus.TOO_MANY_REQUESTS,
                "Hệ thống đang xử lý quá nhiều request tới backend.");
    }

    /**
     * Fallback khi Rate Limiter từ chối (vượt 500 req/s).
     */
    private RestResponse<JsonNode> rateLimitFallback(String operationId,
                                                      Map<String, Object> pathParams,
                                                      Object body,
                                                      Map<String, String> headers,
                                                      Throwable ex) {
        log.warn("RATE LIMITED: operationId={}, vượt giới hạn TPS tới backend", operationId);
        return RestResponse.error(HttpStatus.TOO_MANY_REQUESTS,
                "Vượt giới hạn TPS cho phép gửi tới backend. Vui lòng giảm tải.");
    }

    private RestResponse<JsonNode> rateLimitFallback(String operationId,
                                                      Map<String, Object> pathParams,
                                                      Object body,
                                                      Map<String, String> headers,
                                                      Integer connectTimeoutMs,
                                                      Integer readTimeoutMs,
                                                      Throwable ex) {
        return rateLimitFallback(operationId, pathParams, body, headers, ex);
    }

    private <T> RestResponse<T> rateLimitFallbackTyped(String operationId,
                                                        Map<String, Object> pathParams,
                                                        Object body,
                                                        Map<String, String> headers,
                                                        Class<T> responseType,
                                                        Throwable ex) {
        log.warn("RATE LIMITED: operationId={}", operationId);
        return RestResponse.error(HttpStatus.TOO_MANY_REQUESTS,
                "Vượt giới hạn TPS cho phép gửi tới backend.");
    }
}
