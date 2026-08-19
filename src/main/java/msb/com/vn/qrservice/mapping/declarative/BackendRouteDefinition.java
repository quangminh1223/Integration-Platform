package msb.com.vn.qrservice.mapping.declarative;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Khai báo route thủ công cho backend KHÔNG có file swagger — viết trực tiếp trong
 * cùng file mapping, thay cho việc phải import swagger trước.
 *
 * <p>Khi một {@link ApiMappingDefinition} có khai báo {@code route}, {@link ApiMappingRegistry}
 * tự dựng route và đăng ký vào {@code DynamicRouteRegistry} — cùng registry mà
 * {@code SwaggerAutoLoader} dùng khi parse file swagger. Nhờ vậy toàn bộ phần sau
 * ({@code ResilientBackendCaller}: circuit breaker, bulkhead, retry, transaction log)
 * hoạt động giống hệt, không phân biệt route đến từ swagger hay khai báo tay.</p>
 *
 * <p>Nếu {@link ApiMappingDefinition#getRoute()} là {@code null}, hệ thống giữ hành vi
 * cũ: giả định operationId đã có route từ swagger import.</p>
 */
@Data
@NoArgsConstructor
public class BackendRouteDefinition {

    /** HTTP method gọi backend, vd POST, GET, PUT */
    private String method = "POST";

    /**
     * Path trên backend, có thể chứa path param dạng {@code {id}}.
     * Ghép với {@code baseUrl} để ra URL đầy đủ.
     */
    private String path;

    /**
     * Base URL của backend, vd {@code http://core-banking.internal:8080/api/v1}.
     * Bắt buộc khi không có swagger — đây là nguồn duy nhất xác định backend ở đâu.
     */
    private String baseUrl;

    /** Timeout đọc response (ms). Không khai thì dùng default của ResilientBackendCaller. */
    private Integer timeoutMs;
}
