package msb.com.vn.qrservice.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cache "bất kỳ bảng nào" từ DB lên Redis — KHÔNG cần tạo class model cho từng bảng.
 *
 * Mỗi dòng DB được đọc thành {@code Map<TÊN_CỘT, giá_trị>} (qua {@link JdbcTemplate})
 * rồi lưu vào 1 Redis hash:
 *   key   = tên bảng
 *   field = giá trị cột khóa (vd SPF_CODE / ATZ_ID)
 *   value = cả dòng dưới dạng Map (JSON)
 *
 * Ví dụ:
 * <pre>
 *   dbTableCache.reload("ESB_SRV_PROFILE", "SPF_CODE");   // nạp 670 dòng lên Redis
 *   Map&lt;String,Object&gt; row = dbTableCache.getRow("ESB_SRV_PROFILE", "SRV979");
 *   String url = (String) row.get("SPF_URL");
 * </pre>
 *
 * Khi {@code system-cache.redis.enabled=false} thì các thao tác tự bỏ qua / trả rỗng.
 */
@Slf4j
@Component
public class DbTableCache {

    /** Chỉ cho phép tên bảng/cột hợp lệ (chống SQL injection vì ghép chuỗi vào SQL). */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_$.]{1,128}");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<SystemCacheStore> storeProvider;

    public DbTableCache(JdbcTemplate jdbcTemplate, ObjectProvider<SystemCacheStore> storeProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.storeProvider = storeProvider;
    }

    /**
     * Đọc toàn bộ bảng từ DB và nạp lên Redis (thay thế dữ liệu cũ của bảng).
     *
     * @param table     tên bảng (vd "ESB_SRV_PROFILE")
     * @param keyColumn cột làm khóa field trong hash (vd "SPF_CODE")
     * @return số dòng đã cache
     */
    public int reload(String table, String keyColumn) {
        SystemCacheStore store = store();
        if (store == null) {
            log.debug("Redis chưa bật — bỏ qua reload bảng {}", table);
            return 0;
        }
        validate(table);
        validate(keyColumn);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table);
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyColumn);
            if (k != null) {
                byKey.put(String.valueOf(k), row);
            }
        }
        store.putAll(table, byKey);
        log.info("Đã cache {} dòng từ bảng {} lên Redis (key={})", byKey.size(), table, keyColumn);
        return byKey.size();
    }

    /** Lấy 1 dòng theo khóa — trả về Map<TÊN_CỘT, giá_trị>, null nếu không có. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> getRow(String table, String key) {
        SystemCacheStore store = store();
        if (store == null) return null;
        return (Map<String, Object>) store.get(table, key, (Class) Map.class).orElse(null);
    }

    /** Lấy toàn bộ bảng từ cache: map khóa → dòng. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Map<String, Object>> getAll(String table) {
        SystemCacheStore store = store();
        if (store == null) return Map.of();
        return (Map) store.loadMap(table, (Class) Map.class);
    }

    /** Xóa cache của 1 bảng. */
    public void evict(String table) {
        SystemCacheStore store = store();
        if (store != null) {
            store.delete(table);
        }
    }

    /** true nếu Redis cache đang bật (system-cache.redis.enabled=true). */
    public boolean isEnabled() {
        return store() != null;
    }

    private void validate(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Tên bảng/cột không hợp lệ: " + identifier);
        }
    }

    private SystemCacheStore store() {
        return storeProvider.getIfAvailable();
    }
}
