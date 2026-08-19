package msb.com.vn.qrservice.mapping.declarative;

import msb.com.vn.qrservice.common.exception.QrException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Ném ra khi mapping khai báo phát hiện field bắt buộc bị thiếu.
 *
 * <p>Phân biệt rõ hai chiều lỗi vì trách nhiệm khác nhau:</p>
 * <ul>
 *   <li>{@link Phase#REQUEST} — input từ caller thiếu field bắt buộc → lỗi của CALLER,
 *       trả HTTP 400. Đây là lỗi thay cho việc âm thầm gán giá trị giả như
 *       {@code getString(input, "tranCode", "PAYOO001")} trước đây.</li>
 *   <li>{@link Phase#RESPONSE} — backend trả về thiếu field hợp đồng đã khai báo → lỗi của
 *       BACKEND/hợp đồng tích hợp, trả HTTP 502. Đây là lỗi thay cho việc âm thầm trả
 *       {@code ""} khiến hệ thống chạy "bình thường" với dữ liệu sai.</li>
 * </ul>
 */
public class FieldMappingException extends QrException {

    private final String operationId;
    private final Phase phase;
    private final List<String> missingFields;

    public FieldMappingException(String operationId, Phase phase, List<String> missingFields) {
        super(buildMessage(operationId, phase, missingFields),
                phase == Phase.REQUEST ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY,
                phase == Phase.REQUEST ? "MAPPING_REQUEST_INVALID" : "MAPPING_RESPONSE_INVALID");
        this.operationId = operationId;
        this.phase = phase;
        this.missingFields = missingFields;
    }

    private static String buildMessage(String operationId, Phase phase, List<String> missingFields) {
        String who = phase == Phase.REQUEST
                ? "Request thiếu field bắt buộc"
                : "Backend trả về thiếu field hợp đồng đã khai báo";
        return String.format("%s cho operationId=%s: %s", who, operationId, missingFields);
    }

    public String getOperationId() {
        return operationId;
    }

    public Phase getPhase() {
        return phase;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public enum Phase {
        REQUEST, RESPONSE
    }
}
