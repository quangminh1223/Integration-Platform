package msb.com.vn.qrservice.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Đọc config từ file riêng: backend-endpoints.yml
 * Load 1 lần khi khởi động → cache vào memory → không đọc file lại.
 *
 * Cache structure:
 *   swaggerId → { baseUrl, timeoutMs, domainGroup }
 *
 * Gọi refresh() nếu muốn reload thủ công (ví dụ qua API admin).
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "backend-endpoints")
@PropertySource(value = "classpath:backend-endpoints.yml", factory = YamlPropertySourceFactory.class)
public class BackendEndpointConfig {

    private Map<String, DomainConfig> domains = new HashMap<>();

    // ─── Cache (load 1 lần, lookup O(1)) ──────────────────────────────────────

    /** Cache: swaggerId → base URL */
    private final ConcurrentHashMap<String, String> urlCache = new ConcurrentHashMap<>();

    /** Cache: swaggerId → timeout (ms) */
    private final ConcurrentHashMap<String, Integer> timeoutCache = new ConcurrentHashMap<>();

    /** Cache: swaggerId → domain group name */
    private final ConcurrentHashMap<String, String> groupCache = new ConcurrentHashMap<>();

    private volatile boolean cacheLoaded = false;

    // ─── Init: build cache 1 lần ─────────────────────────────────────────────

    @PostConstruct
    public void init() {
        buildCache();
        logConfig();
    }

    /**
     * Build cache từ config. Gọi 1 lần khi khởi động.
     * Có thể gọi lại nếu muốn refresh.
     */
    public synchronized void buildCache() {
        urlCache.clear();
        timeoutCache.clear();
        groupCache.clear();

        if (domains == null || domains.isEmpty()) {
            cacheLoaded = true;
            return;
        }

        domains.forEach((group, config) -> {
            if (config.getSwaggerFiles() == null) return;

            config.getSwaggerFiles().forEach((swaggerId, overrideUrl) -> {
                // URL: override nếu có, không thì dùng base-url nhóm
                String url = (overrideUrl != null && !overrideUrl.isBlank())
                        ? overrideUrl
                        : config.getBaseUrl();
                if (url != null) {
                    urlCache.put(swaggerId, url);
                }

                // Timeout
                if (config.getTimeoutMs() != null) {
                    timeoutCache.put(swaggerId, config.getTimeoutMs());
                }

                // Group
                groupCache.put(swaggerId, group);
            });
        });

        cacheLoaded = true;
        log.info("BackendEndpointConfig cache built: {} entries", urlCache.size());
    }

    /**
     * Refresh cache (gọi từ API admin khi cần reload config).
     */
    public void refresh() {
        log.info("Refreshing backend endpoint cache...");
        buildCache();
    }

    // ─── Lookup (từ cache, O(1)) ──────────────────────────────────────────────

    public String getBaseUrl(String swaggerId) {
        return urlCache.get(swaggerId);
    }

    public Integer getTimeoutMs(String swaggerId) {
        return timeoutCache.get(swaggerId);
    }

    public String getDomainGroup(String swaggerId) {
        return groupCache.get(swaggerId);
    }

    public boolean hasConfig(String swaggerId) {
        return urlCache.containsKey(swaggerId);
    }

    public int getCacheSize() {
        return urlCache.size();
    }

    // ─── Log ──────────────────────────────────────────────────────────────────

    private void logConfig() {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  BACKEND ENDPOINTS (cached from backend-endpoints.yml)");
        if (urlCache.isEmpty()) {
            log.info("  (Chưa cấu hình domain nào)");
        } else {
            urlCache.forEach((swaggerId, url) ->
                    log.info("  [{}] {} → {} (timeout={}ms)",
                            groupCache.getOrDefault(swaggerId, "?"),
                            swaggerId, url,
                            timeoutCache.getOrDefault(swaggerId, -1)));
        }
        log.info("═══════════════════════════════════════════════════════════════");
    }

    // ─── Inner class ──────────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class DomainConfig {
        private String description;
        private String baseUrl;
        private Integer timeoutMs;
        private Map<String, String> swaggerFiles = new HashMap<>();
    }
}
