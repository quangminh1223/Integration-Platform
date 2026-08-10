package msb.com.vn.qrservice.swagger.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

/**
 * Một route được extract từ Swagger 2.0 — tương ứng với 1 operation (path + method).
 *
 * Ví dụ: POST /transfers → SwaggerRouteDefinition{path="/transfers", method=POST, ...}
 */
@Data
@Builder
public class SwaggerRouteDefinition {

    /** Path trong swagger, ví dụ: /transfers, /users/{id} */
    private String path;

    /** HTTP method */
    private HttpMethod method;

    /** operationId từ swagger */
    private String operationId;

    /** summary mô tả ngắn */
    private String summary;

    /** description mô tả đầy đủ */
    private String description;

    /** Tags phân nhóm */
    private List<String> tags;

    /** Danh sách parameters (path, query, header) */
    private List<ParameterDefinition> parameters;

    /** Schema của request body (nếu có) */
    private JsonNode requestBodySchema;

    /** Map HTTP status code → schema response */
    private Map<String, JsonNode> responseSchemas;

    /** Consumes — content type nhận vào */
    private List<String> consumes;

    /** Produces — content type trả ra */
    private List<String> produces;

    /** Base URL của backend target (lấy từ swagger host + basePath) */
    private String targetBaseUrl;

    /** SwaggerId — tên file swagger (dùng để lookup domain từ config) */
    private String swaggerId;

    // ── Inner: Parameter ──────────────────────────────────────────────────────

    @Data
    @Builder
    public static class ParameterDefinition {
        private String name;
        private String in;          // path | query | header | body | formData
        private String description;
        private boolean required;
        private String type;
        private String format;
        private JsonNode schema;
    }
}
