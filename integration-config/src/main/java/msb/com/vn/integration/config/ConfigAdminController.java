package msb.com.vn.integration.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.model.IntegrationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Admin controller for hot configuration management.
 * Allows runtime config updates without restart.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
public class ConfigAdminController {

    private final HotConfigService hotConfigService;

    /**
     * Get a configuration value.
     */
    @GetMapping("/{key}")
    public ResponseEntity<IntegrationResponse<Object>> getConfig(@PathVariable String key) {
        Optional<String> value = hotConfigService.get(key);
        if (value.isPresent()) {
            return ResponseEntity.ok(IntegrationResponse.success("system",
                    Map.of("key", key, "value", value.get())));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Set a configuration value (hot update).
     */
    @PutMapping("/{key}")
    public ResponseEntity<IntegrationResponse<Object>> setConfig(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().body(
                    IntegrationResponse.error("system", "400", "Missing 'value' in body"));
        }

        hotConfigService.set(key, value);
        log.info("Config updated via API: key={}", key);

        return ResponseEntity.ok(IntegrationResponse.success("system",
                Map.of("key", key, "value", value, "status", "UPDATED")));
    }

    /**
     * Delete a configuration entry.
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<IntegrationResponse<Object>> deleteConfig(@PathVariable String key) {
        hotConfigService.delete(key);
        return ResponseEntity.ok(IntegrationResponse.success("system",
                Map.of("key", key, "status", "DELETED")));
    }

    /**
     * List all configuration keys matching pattern.
     */
    @GetMapping("/keys")
    public ResponseEntity<IntegrationResponse<Object>> listKeys(
            @RequestParam(defaultValue = "*") String pattern) {
        Set<String> keys = hotConfigService.getKeys(pattern);
        return ResponseEntity.ok(IntegrationResponse.success("system",
                Map.of("pattern", pattern, "keys", keys, "count", keys.size())));
    }

    /**
     * Refresh all config from Redis.
     */
    @PostMapping("/refresh")
    public ResponseEntity<IntegrationResponse<Object>> refreshConfig() {
        hotConfigService.refreshCache();
        return ResponseEntity.ok(IntegrationResponse.success("system",
                Map.of("status", "REFRESHED")));
    }
}
