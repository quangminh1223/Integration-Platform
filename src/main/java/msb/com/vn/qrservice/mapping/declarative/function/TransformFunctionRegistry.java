package msb.com.vn.qrservice.mapping.declarative.function;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Đăng ký toàn bộ {@link TransformFunction} có sẵn trong ứng dụng — auto-discovery qua Spring DI,
 * giống {@code PluginRegistry} trong integration-platform. Thêm hàm mới không cần sửa registry.
 */
@Slf4j
@Component
public class TransformFunctionRegistry {

    private final Map<String, TransformFunction> functionsByName = new ConcurrentHashMap<>();

    public TransformFunctionRegistry(List<TransformFunction> functions) {
        for (TransformFunction function : functions) {
            String key = function.name().toUpperCase();
            if (functionsByName.containsKey(key)) {
                log.warn("Hàm transform trùng tên '{}', giữ nguyên bản đăng ký trước", key);
                continue;
            }
            functionsByName.put(key, function);
        }
        log.info("Đã đăng ký {} hàm transform: {}", functionsByName.size(), functionsByName.keySet());
    }

    public Optional<TransformFunction> find(String name) {
        return Optional.ofNullable(functionsByName.get(name.toUpperCase()));
    }

    public boolean exists(String name) {
        return functionsByName.containsKey(name.toUpperCase());
    }
}
