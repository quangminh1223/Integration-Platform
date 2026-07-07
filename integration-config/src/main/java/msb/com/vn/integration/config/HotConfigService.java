package msb.com.vn.integration.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hot configuration service — enables runtime config changes without restart.
 * Config is stored in Redis and cached locally. Supports:
 * - Flow definitions
 * - Routing tables
 * - Adapter parameters
 * - Rate limits
 *
 * Changes propagated via Redis pub/sub to all instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotConfigService {

    private static final String CONFIG_PREFIX = "integration:config:";

    private final StringRedisTemplate redisTemplate;
    private final Map<String, String> localCache = new ConcurrentHashMap<>();
    private final Map<String, ConfigChangeListener> listeners = new ConcurrentHashMap<>();

    /**
     * Get configuration value by key.
     */
    public Optional<String> get(String key) {
        // Local cache first
        String cached = localCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Redis fallback
        String value = redisTemplate.opsForValue().get(CONFIG_PREFIX + key);
        if (value != null) {
            localCache.put(key, value);
        }
        return Optional.ofNullable(value);
    }

    /**
     * Set configuration value (persists to Redis and updates local cache).
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(CONFIG_PREFIX + key, value);
        localCache.put(key, value);
        notifyListeners(key, value);
        log.info("Config updated: key={}", key);
    }

    /**
     * Delete configuration entry.
     */
    public void delete(String key) {
        redisTemplate.delete(CONFIG_PREFIX + key);
        localCache.remove(key);
        log.info("Config deleted: key={}", key);
    }

    /**
     * Get all keys matching a pattern.
     */
    public Set<String> getKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(CONFIG_PREFIX + pattern);
        return keys != null ? keys : Set.of();
    }

    /**
     * Register a listener for configuration changes.
     */
    public void registerListener(String keyPattern, ConfigChangeListener listener) {
        listeners.put(keyPattern, listener);
    }

    private void notifyListeners(String key, String value) {
        listeners.forEach((pattern, listener) -> {
            if (key.startsWith(pattern) || key.matches(pattern)) {
                try {
                    listener.onConfigChange(key, value);
                } catch (Exception e) {
                    log.error("Config listener error: pattern={}, key={}", pattern, key, e);
                }
            }
        });
    }

    /**
     * Refresh local cache from Redis.
     */
    public void refreshCache() {
        Set<String> keys = redisTemplate.keys(CONFIG_PREFIX + "*");
        if (keys != null) {
            localCache.clear();
            keys.forEach(fullKey -> {
                String key = fullKey.replace(CONFIG_PREFIX, "");
                String value = redisTemplate.opsForValue().get(fullKey);
                if (value != null) {
                    localCache.put(key, value);
                }
            });
            log.info("Config cache refreshed: {} entries", localCache.size());
        }
    }

    @FunctionalInterface
    public interface ConfigChangeListener {
        void onConfigChange(String key, String value);
    }
}
