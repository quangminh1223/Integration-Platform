package msb.com.vn.jsontransform;

/**
 * Lỗi cú pháp script — xảy ra lúc COMPILE script, không phải lúc chạy transform.
 *
 * <p>Đây là điểm thiết kế có chủ đích: tên hàm sai, thiếu {@code ;}, sai số tham số,
 * thiếu nháy đơn đóng... đều lộ ra ngay khi nạp script, không đợi tới lúc có dữ liệu thật
 * mới phát hiện.</p>
 */
public class JsonTransformSyntaxException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int lineNumber;

    public JsonTransformSyntaxException(int lineNumber, String reason) {
        super("Dòng " + lineNumber + ": " + reason);
        this.lineNumber = lineNumber;
    }

    /** Số dòng vật lý trong script nơi phát hiện lỗi, để sửa file cho nhanh. */
    public int getLineNumber() {
        return lineNumber;
    }
}
