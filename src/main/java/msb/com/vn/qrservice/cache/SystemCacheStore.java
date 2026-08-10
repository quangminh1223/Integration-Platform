package msb.com.vn.qrservice.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lưu / load "cache hệ thống" lên Redis.
 *
 * Bean này CHỈ được tạo khi cấu hình {@code system-cache.redis.enabled=true}.
 * Khi chưa có thông tin kết nối Redis (mặc định tắt), bean không tồn tại và
 * các thành phần dùng nó (vd {@code DynamicRouteRegistry}) tự fallback về
 * in-memory, nên ứng dụng vẫn chạy bình thường.
 *
 * Hỗ trợ 2 kiểu lưu:
 *  - Key/Value đơn: {@link #save}, {@link #load}
 *  - Hash (1 key chứa nhiều field): {@link #putAll}, {@link #loadMap} —
 *    phù hợp để lưu cả "bảng" cache (vd map route) dưới 1 key.
 *
 * Serialization dùng {@code RedisTemplate<String,Object>} đã cấu hình ở
 * {@code RedisConfig} (GenericJackson2JsonRedisSerializer có lưu type info),
 * nên object đọc ra giữ đúng kiểu gốc.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "system-cache.redis", name = "enabled", havingValue = "true")
public class SystemCacheStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;
    private final Duration defaultTtl;

    public SystemCacheStore(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${system-cache.redis.key-prefix:syscache:}") String keyPrefix,
            @Value("${system-cache.redis.ttl-seconds:0}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.defaultTtl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : null;
        log.info("SystemCacheStore enabled — prefix='{}', ttl={}",
                keyPrefix, defaultTtl != null ? defaultTtl : "không hết hạn");
    }

    // ─── Key/Value đơn ──────────────────────────────────────────────────────────

    /** Lưu 1 giá trị với TTL mặc định (nếu cấu hình). */
    public void save(String key, Object value) {
        save(key, value, defaultTtl);
    }

    /** Lưu 1 giá trị với TTL chỉ định (null = không hết hạn). */
    public void save(String key, Object value, Duration ttl) {
        try {
            String k = fullKey(key);
            if (ttl != null) {
                redisTemplate.opsForValue().set(k, value, ttl);
            } else {
                redisTemplate.opsForValue().set(k, value);
            }
        } catch (Exception e) {
            log.error("Lỗi lưu cache key='{}' lên Redis: {}", key, e.getMessage());
        }
    }

    /** Load 1 giá trị theo key, ép về kiểu {@code type}. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> load(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(fullKey(key));
            if (value == null) return Optional.empty();
            if (!type.isInstance(value)) {
                log.warn("Cache key='{}' có kiểu {} khác kỳ vọng {}", key,
                        value.getClass().getName(), type.getName());
                return Optional.empty();
            }
            return Optional.of((T) value);
        } catch (Exception e) {
            log.error("Lỗi load cache key='{}' từ Redis: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Hash (1 key chứa nhiều field) ────────────────────────────────────────

    /** Ghi 1 field vào hash. */
    public void put(String hashKey, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(fullKey(hashKey), field, value);
        } catch (Exception e) {
            log.error("Lỗi put hash key='{}' field='{}': {}", hashKey, field, e.getMessage());
        }
    }

    /** Ghi toàn bộ map vào hash (thay thế toàn bộ field hiện có của key). */
    public void putAll(String hashKey, Map<String, ?> values) {
        try {
            String k = fullKey(hashKey);
            redisTemplate.delete(k);
            if (values != null && !values.isEmpty()) {
                redisTemplate.opsForHash().putAll(k, values);
                if (defaultTtl != null) {
                    redisTemplate.expire(k, defaultTtl);
                }
            }
        } catch (Exception e) {
            log.error("Lỗi putAll hash key='{}': {}", hashKey, e.getMessage());
        }
    }

    /** Xóa 1 field khỏi hash. */
    public void removeField(String hashKey, String field) {
        try {
            redisTemplate.opsForHash().delete(fullKey(hashKey), field);
        } catch (Exception e) {
            log.error("Lỗi xóa field='{}' của hash key='{}': {}", field, hashKey, e.getMessage());
        }
    }

    /** Load 1 field của hash, ép về kiểu {@code valueType}. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String hashKey, String field, Class<T> valueType) {
        try {
            Object v = redisTemplate.opsForHash().get(fullKey(hashKey), field);
            if (v == null) return Optional.empty();
            if (!valueType.isInstance(v)) {
                log.warn("Hash key='{}' field='{}' có kiểu {} khác kỳ vọng {}", hashKey, field,
                        v.getClass().getName(), valueType.getName());
                return Optional.empty();
            }
            return Optional.of((T) v);
        } catch (Exception e) {
            log.error("Lỗi get hash key='{}' field='{}': {}", hashKey, field, e.getMessage());
            return Optional.empty();
        }
    }

    /** Load toàn bộ hash về một map (giữ thứ tự chèn). */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> loadMap(String hashKey, Class<T> valueType) {
        Map<String, T> result = new LinkedHashMap<>();
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(fullKey(hashKey));
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                Object v = e.getValue();
                if (valueType.isInstance(v)) {
                    result.put(String.valueOf(e.getKey()), (T) v);
                }
            }
        } catch (Exception e) {
            log.error("Lỗi loadMap hash key='{}': {}", hashKey, e.getMessage());
        }
        return result;
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────────

    /** Xóa 1 key (kể cả hash). */
    public void delete(String key) {
        try {
            redisTemplate.delete(fullKey(key));
        } catch (Exception e) {
            log.error("Lỗi xóa cache key='{}': {}", key, e.getMessage());
        }
    }

    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey(key)));
        } catch (Exception e) {
            log.error("Lỗi kiểm tra cache key='{}': {}", key, e.getMessage());
            return false;
        }
    }

    private String fullKey(String key) {
        return keyPrefix + key;
    }

    /** Cho thành phần khác lấy map rỗng an toàn. */
    public static <T> Map<String, T> emptyMap() {
        return Collections.emptyMap();
    }
}
