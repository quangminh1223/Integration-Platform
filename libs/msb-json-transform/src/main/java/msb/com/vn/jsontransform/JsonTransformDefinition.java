package msb.com.vn.jsontransform;

import java.util.List;

/**
 * Một script transform đã compile — bất biến (immutable) và an toàn khi dùng đồng thời
 * từ nhiều thread.
 *
 * <p>Compile một lần lúc khởi động rồi tái sử dụng cho mọi request: toàn bộ việc tokenize,
 * parse và validate tên hàm/số tham số đã xong ở bước compile, lúc chạy chỉ còn duyệt cây
 * biểu thức.</p>
 */
public final class JsonTransformDefinition {

    private final String operationId;
    private final List<TransformAssignment> assignments;
    private final String rawSource;

    JsonTransformDefinition(String operationId, List<TransformAssignment> assignments, String rawSource) {
        this.operationId = operationId;
        this.assignments = List.copyOf(assignments);
        this.rawSource = rawSource;
    }

    /** Định danh của transform, thường là tên file không phần mở rộng. */
    public String operationId() {
        return operationId;
    }

    /** Số dòng gán trong script. */
    public int statementCount() {
        return assignments.size();
    }

    /** Nội dung script gốc, giữ lại để debug và hiển thị. */
    public String rawSource() {
        return rawSource;
    }

    /** Danh sách target path mà transform này sẽ ghi ra, theo đúng thứ tự khai báo. */
    public List<String> targetPaths() {
        return assignments.stream().map(TransformAssignment::targetPath).toList();
    }

    /** Nội bộ lib — engine cần danh sách gán để thực thi. */
    List<TransformAssignment> assignments() {
        return assignments;
    }

    @Override
    public String toString() {
        return "JsonTransformDefinition{operationId='" + operationId
                + "', statements=" + assignments.size() + "}";
    }
}
