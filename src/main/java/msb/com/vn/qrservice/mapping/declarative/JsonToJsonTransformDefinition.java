package msb.com.vn.qrservice.mapping.declarative;

import java.util.List;

/**
 * Toàn bộ tập lệnh gán đã parse từ một file {@code .esql}, gắn với một operationId.
 *
 * @param operationId  định danh dùng để lookup, khớp tên file (không phần mở rộng)
 * @param assignments  danh sách dòng gán theo đúng thứ tự khai báo trong file
 * @param rawSource    nội dung file gốc, giữ lại để debug/hiển thị
 */
public record JsonToJsonTransformDefinition(
        String operationId,
        List<JsonToJsonAssignment> assignments,
        String rawSource
) {
}
