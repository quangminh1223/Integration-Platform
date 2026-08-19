package msb.com.vn.qrservice.mapping.declarative;

import msb.com.vn.qrservice.common.exception.QrException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Ném ra lúc XỬ LÝ REQUEST khi một dòng gán được đánh dấu {@code required} (comment cuối dòng
 * chứa "required") nhưng input không có giá trị cho field nguồn.
 *
 * <p>Đi qua {@code GlobalExceptionHandler} như mọi {@link QrException} khác, trả HTTP 400
 * vì đây là dữ liệu đầu vào không đủ theo hợp đồng transform đã khai báo.</p>
 */
public class JsonToJsonTransformException extends QrException {

    private final String operationId;
    private final List<String> missingStatements;

    public JsonToJsonTransformException(String operationId, List<String> missingStatements) {
        super("Transform " + operationId + " thiếu giá trị cho " + missingStatements.size()
                        + " field bắt buộc: " + missingStatements,
                HttpStatus.BAD_REQUEST, "TRANSFORM_MISSING_REQUIRED_FIELD");
        this.operationId = operationId;
        this.missingStatements = missingStatements;
    }

    public String getOperationId() {
        return operationId;
    }

    public List<String> getMissingStatements() {
        return missingStatements;
    }
}
