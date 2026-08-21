package msb.com.vn.jsontransform;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bảng tra hàm transform theo tên, không phân biệt hoa/thường.
 *
 * <p>Không auto-discovery: tập hàm mặc định là {@link StringFunctions#values()}, hàm riêng
 * truyền vào lúc dựng. Đọc code là biết chính xác những hàm nào có mặt.</p>
 */
public final class TransformFunctionRegistry {

    private final Map<String, TransformFunction> byName;

    /**
     * @param functions hàm trùng tên thì bản ĐẦU TIÊN được giữ, nên hàm riêng truyền vào
     *                  trước có thể ghi đè hàm mặc định cùng tên
     */
    public TransformFunctionRegistry(Collection<? extends TransformFunction> functions) {
        Map<String, TransformFunction> map = new LinkedHashMap<>();
        for (TransformFunction function : functions) {
            if (function != null) {
                map.putIfAbsent(function.functionName().toUpperCase(), function);
            }
        }
        this.byName = Map.copyOf(map);
    }

    /** Registry chỉ gồm 12 hàm mặc định trong {@link StringFunctions}. */
    public static TransformFunctionRegistry withBuiltIn() {
        return new TransformFunctionRegistry(List.of(StringFunctions.values()));
    }

    public Optional<TransformFunction> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name.toUpperCase()));
    }

    /** Tên các hàm đã đăng ký, dùng trong thông báo lỗi để tra nhanh. */
    public List<String> registeredNames() {
        return byName.keySet().stream().sorted().toList();
    }

    public int size() {
        return byName.size();
    }
}
