package msb.com.vn.qrservice.mapping.declarative;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Toàn bộ khai báo mapping cho một API backend — nạp từ 1 file YAML.
 *
 * <p>Thêm API mới = thêm 1 file YAML trong {@code mapping-definitions/}, không cần viết
 * class Java hay DTO nào. So khớp với {@code operationId} trong swagger đã import.</p>
 */
@Data
@NoArgsConstructor
public class ApiMappingDefinition {

    /** Phải khớp operationId trong swagger đã import — dùng để lookup lúc gọi backend */
    private String operationId;

    /** Mô tả ngắn, chỉ để log/debug */
    private String description;

    /** Mapping chiều request: input nội bộ → JSON body gửi backend */
    private List<FieldMapping> request = new ArrayList<>();

    /** Mapping chiều response: JSON backend trả về → output đơn giản */
    private List<FieldMapping> response = new ArrayList<>();

    /**
     * Khai báo route thủ công — CHỈ cần khi backend không có swagger.
     * Có giá trị này thì {@link ApiMappingRegistry} tự đăng ký route vào
     * {@code DynamicRouteRegistry}, không cần import swagger trước.
     * Để trống (null) nếu operationId đã có route từ swagger import — giữ hành vi cũ.
     */
    private BackendRouteDefinition route;
}
