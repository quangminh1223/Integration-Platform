package msb.com.vn.qrservice.swagger.registry;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.swagger.model.SwaggerRouteDefinition;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry lưu trữ tất cả route động được import từ Swagger.
 *
 * Key: "{METHOD}:{path}" — ví dụ "POST:/transfers", "GET:/users/{id}"
 * Thread-safe với ConcurrentHashMap.
 */
@Slf4j
@Component
public class DynamicRouteRegistry {

    // key = "METHOD:path", value = route definition
    private final Map<String, SwaggerRouteDefinition> routes = new ConcurrentHashMap<>();

    // swaggerId → list of route keys (để xóa theo swagger)
    private final Map<String, List<String>> swaggerRouteIndex = new ConcurrentHashMap<>();

    // ─── Register ─────────────────────────────────────────────────────────────

    /**
     * Đăng ký một route mới. Nếu đã tồn tại thì override.
     */
    public void register(String swaggerId, SwaggerRouteDefinition route) {
        String key = buildKey(route.getMethod(), route.getPath());
        routes.put(key, route);
        swaggerRouteIndex.computeIfAbsent(swaggerId, k -> new ArrayList<>()).add(key);
        log.info("Registered route: {} {} (operationId={})", route.getMethod(), route.getPath(), route.getOperationId());
    }

    /**
     * Đăng ký nhiều route cùng lúc.
     */
    public void registerAll(String swaggerId, List<SwaggerRouteDefinition> routeList) {
        // Xóa routes cũ của swagger này trước (nếu re-import)
        deregisterBySwaggerId(swaggerId);
        routeList.forEach(r -> register(swaggerId, r));
        log.info("Registered {} routes for swaggerId={}", routeList.size(), swaggerId);
    }

    // ─── Lookup ───────────────────────────────────────────────────────────────

    /**
     * Tìm route khớp với method + path.
     * Hỗ trợ path template: /users/{id} khớp với /users/123
     */
    public Optional<SwaggerRouteDefinition> find(HttpMethod method, String requestPath) {
        // 1. Tìm exact match trước
        String exactKey = buildKey(method, requestPath);
        if (routes.containsKey(exactKey)) {
            return Optional.of(routes.get(exactKey));
        }

        // 2. Tìm template match: /users/{id} ~ /users/123
        return routes.values().stream()
                .filter(r -> r.getMethod() == method)
                .filter(r -> pathMatches(r.getPath(), requestPath))
                .findFirst();
    }

    /**
     * Lấy tất cả routes hiện tại.
     */
    public Collection<SwaggerRouteDefinition> getAllRoutes() {
        return Collections.unmodifiableCollection(routes.values());
    }

    /**
     * Lấy routes theo swaggerId.
     */
    public List<SwaggerRouteDefinition> getRoutesBySwaggerId(String swaggerId) {
        List<String> keys = swaggerRouteIndex.getOrDefault(swaggerId, List.of());
        return keys.stream()
                .map(routes::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Xóa tất cả routes của một swagger.
     */
    public void deregisterBySwaggerId(String swaggerId) {
        List<String> keys = swaggerRouteIndex.remove(swaggerId);
        if (keys != null) {
            keys.forEach(routes::remove);
            log.info("Deregistered {} routes for swaggerId={}", keys.size(), swaggerId);
        }
    }

    /**
     * Xóa toàn bộ routes.
     */
    public void clear() {
        routes.clear();
        swaggerRouteIndex.clear();
        log.warn("Cleared all dynamic routes");
    }

    public int size() {
        return routes.size();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String buildKey(HttpMethod method, String path) {
        return method.name() + ":" + normalizePath(path);
    }

    private String normalizePath(String path) {
        if (path == null) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Kiểm tra path template có khớp với request path không.
     * /users/{id}/orders/{orderId} ~ /users/123/orders/456 → true
     */
    private boolean pathMatches(String template, String requestPath) {
        String[] tParts = normalizePath(template).split("/");
        String[] rParts = normalizePath(requestPath).split("/");

        if (tParts.length != rParts.length) return false;

        for (int i = 0; i < tParts.length; i++) {
            String t = tParts[i];
            String r = rParts[i];
            // {variable} khớp với bất kỳ segment nào
            if (!t.startsWith("{") && !t.equals(r)) {
                return false;
            }
        }
        return true;
    }
}
