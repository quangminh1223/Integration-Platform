package msb.com.vn.qrservice.mapping.declarative.function;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Một hàm dùng trong biểu thức của file transform (cú pháp rút gọn kiểu ESQL).
 *
 * <p>Thêm hàm mới = tạo 1 class implement interface này, đăng ký trong
 * {@link TransformFunctionRegistry}. Không cần sửa parser hay engine.</p>
 */
public interface TransformFunction {

    /** Tên hàm, dùng đúng chính tả trong file transform — không phân biệt hoa/thường khi so khớp. */
    String name();

    /**
     * Số tham số bắt buộc. Trả về -1 nếu hàm nhận số lượng tham số biến đổi
     * (vd {@code CONCAT} nhận từ 2 tham số trở lên) — khi đó dùng {@link #minArgCount()}.
     */
    int argCount();

    /** Số tham số tối thiểu khi {@link #argCount()} trả về -1 (hàm biến đổi số lượng tham số). */
    default int minArgCount() {
        return argCount();
    }

    /**
     * Thực thi hàm trên các tham số đã được resolve thành JsonNode.
     * Tham số có thể là {@code null} (field nguồn không có giá trị) — hàm tự quyết định
     * cách xử lý null theo đúng ngữ nghĩa ESQL (thường là trả về null nếu bất kỳ tham số
     * nào null, trừ hàm xử lý null tường minh như COALESCE).
     */
    JsonNode apply(List<JsonNode> args);
}
