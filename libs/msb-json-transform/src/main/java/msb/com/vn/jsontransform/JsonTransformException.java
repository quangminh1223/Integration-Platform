package msb.com.vn.jsontransform;

import java.util.List;

/**
 * Ném ra lúc CHẠY transform khi một dòng gán được đánh dấu {@code required} nhưng
 * biểu thức nguồn không cho ra giá trị.
 *
 * <p>Lib không phụ thuộc web framework nào nên exception này là {@link RuntimeException}
 * thuần. Bên gọi tự quyết định map sang HTTP status nào — thường là 400, vì nguyên nhân
 * là dữ liệu đầu vào không đủ so với hợp đồng transform đã khai báo.</p>
 */
public class JsonTransformException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String operationId;

    /**
     * {@link List#copyOf} trả về một implementation serializable, nên field này an toàn khi
     * serialize exception. javac không chứng minh được điều đó qua kiểu {@code List} nên
     * cảnh báo [serial] — tắt tại đây thay vì đổi sang kiểu cụ thể và mất tính bất biến.
     */
    @SuppressWarnings("serial")
    private final List<String> missingStatements;

    public JsonTransformException(String operationId, List<String> missingStatements) {
        super("Transform " + operationId + " thiếu giá trị cho " + missingStatements.size()
                + " field bắt buộc: " + missingStatements);
        this.operationId = operationId;
        this.missingStatements = List.copyOf(missingStatements);
    }

    public String getOperationId() {
        return operationId;
    }

    /** Danh sách statement gốc (nguyên văn) của các field bắt buộc bị thiếu. */
    public List<String> getMissingStatements() {
        return missingStatements;
    }
}
