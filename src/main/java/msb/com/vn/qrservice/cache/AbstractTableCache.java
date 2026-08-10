package msb.com.vn.qrservice.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Base class generic để cache 1 "bảng" lên Redis — dùng lại cho mọi bảng,
 * khỏi viết lặp logic save/load.
 *
 * Mỗi bảng = 1 Redis hash:
 *   key   = {@code hashKey} (vd "service-routes")
 *   field = khóa của record (lấy qua {@code keyExtractor}, vd SPF_CODE)
 *   value = record (JSON)
 *
 * Cách tạo cache cho 1 bảng mới: tạo 1 class con cực ngắn, vd:
 * <pre>
 * &#64;Component
 * public class PartnerCache extends AbstractTableCache&lt;Partner&gt; {
 *     public PartnerCache(ObjectProvider&lt;SystemCacheStore&gt; p) {
 *         super(p, "partners", Partner.class, Partner::getCode);
 *     }
 * }
 * </pre>
 *
 * Khi {@code system-cache.redis.enabled=false}, {@link SystemCacheStore} không
 * tồn tại nên mọi thao tác tự fallback (đọc trả rỗng, ghi bỏ qua) — app vẫn chạy.
 *
 * @param <T> kiểu record của bảng
 */
@Slf4j
public abstract class AbstractTableCache<T> {

    private final ObjectProvider<SystemCacheStore> storeProvider;
    private final String hashKey;
    private final Class<T> type;
    private final Function<T, String> keyExtractor;

    protected AbstractTableCache(ObjectProvider<SystemCacheStore> storeProvider,
                                 String hashKey,
                                 Class<T> type,
                                 Function<T, String> keyExtractor) {
        this.storeProvider = storeProvider;
        this.hashKey = hashKey;
        this.type = type;
        this.keyExtractor = keyExtractor;
    }

    /** Lưu toàn bộ (thay thế dữ liệu cũ của bảng). */
    public void saveAll(List<T> records) {
        SystemCacheStore store = store();
        if (store == null || records == null) return;
        Map<String, T> map = records.stream()
                .collect(Collectors.toMap(
                        keyExtractor,
                        r -> r,
                        (a, b) -> b,
                        java.util.LinkedHashMap::new));
        store.putAll(hashKey, map);
        log.info("Cache '{}': đã lưu {} record lên Redis", hashKey, map.size());
    }

    /** Lưu / cập nhật 1 record. */
    public void save(T record) {
        SystemCacheStore store = store();
        if (store != null && record != null) {
            store.put(hashKey, keyExtractor.apply(record), record);
        }
    }

    /** Load 1 record theo khóa. */
    public Optional<T> findByKey(String key) {
        SystemCacheStore store = store();
        if (store == null) return Optional.empty();
        return store.get(hashKey, key, type);
    }

    /** Load toàn bộ bảng về map khóa → record. */
    public Map<String, T> findAll() {
        SystemCacheStore store = store();
        if (store == null) return Map.of();
        return store.loadMap(hashKey, type);
    }

    /** Xóa 1 record khỏi cache. */
    public void remove(String key) {
        SystemCacheStore store = store();
        if (store != null) {
            store.removeField(hashKey, key);
        }
    }

    /** Xóa toàn bộ bảng trong cache. */
    public void clear() {
        SystemCacheStore store = store();
        if (store != null) {
            store.delete(hashKey);
        }
    }

    /** Tên hash key của bảng trên Redis. */
    protected String hashKey() {
        return hashKey;
    }

    private SystemCacheStore store() {
        return storeProvider.getIfAvailable();
    }
}
