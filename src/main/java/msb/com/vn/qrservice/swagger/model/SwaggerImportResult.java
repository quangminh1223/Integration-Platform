package msb.com.vn.qrservice.swagger.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kết quả sau khi import một file Swagger.
 */
@Data
@Builder
public class SwaggerImportResult {

    private String swaggerId;           // ID định danh swagger đã import
    private String title;               // info.title
    private String version;             // info.version
    private String targetBaseUrl;       // host + basePath
    private int totalRoutes;            // tổng số route extract được
    private List<RouteInfo> routes;     // danh sách route
    private LocalDateTime importedAt;
    private List<String> warnings;      // cảnh báo nếu có field không hỗ trợ

    @Data
    @Builder
    public static class RouteInfo {
        private String method;
        private String path;
        private String operationId;
        private String summary;
        private String registeredEndpoint; // endpoint thực tế trên service này
    }
}
