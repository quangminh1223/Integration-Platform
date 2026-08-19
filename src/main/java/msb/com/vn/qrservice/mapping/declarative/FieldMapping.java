package msb.com.vn.qrservice.mapping.declarative;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Khai báo mapping cho một field, đọc từ file YAML — không cần code Java.
 *
 * <h3>Ngữ nghĩa xử lý khi field nguồn không có giá trị (null/rỗng):</h3>
 * <ol>
 *   <li>Thử từng {@code fallbackSources} theo thứ tự, dùng giá trị đầu tiên khác rỗng</li>
 *   <li>Nếu vẫn rỗng và có {@code defaultValue} → dùng giá trị mặc định</li>
 *   <li>Nếu vẫn rỗng và {@code required=true} → ném lỗi rõ ràng, liệt kê đúng field thiếu</li>
 *   <li>Nếu vẫn rỗng và {@code required=false} (mặc định) → BỎ QUA field này hoàn toàn,
 *       không ghi key rỗng, không đoán giá trị</li>
 * </ol>
 *
 * <p>Đây là điểm khác biệt cốt lõi so với việc viết tay {@code getString(input, key, "default")}:
 * một field bắt buộc (như mã giao dịch) không bao giờ được phép âm thầm nhận giá trị mặc định —
 * nó phải được khai báo {@code required: true} và không có {@code defaultValue}.</p>
 */
@Data
@NoArgsConstructor
public class FieldMapping {

    /**
     * Đường dẫn tới field nguồn.
     * <ul>
     *   <li>Chiều request: dot-path vào input Map, vd {@code debitAccount}, {@code customer.id}</li>
     *   <li>Chiều response: dot-path vào JsonNode backend trả về, vd {@code header.id}</li>
     * </ul>
     */
    private String source;

    /**
     * Đường dẫn tới field đích.
     * <ul>
     *   <li>Chiều request: dot-path vào JSON body gửi backend, hỗ trợ mảng —
     *       vd {@code body.paymentDetails[0].paymentDetail}</li>
     *   <li>Chiều response: key trong Map kết quả trả về, vd {@code transactionId}</li>
     * </ul>
     */
    private String target;

    /**
     * Các đường dẫn thử thêm theo thứ tự nếu {@code source} rỗng.
     * Dùng cho trường hợp một giá trị có thể đến từ nhiều field khác nhau,
     * vd {@code creditRate} hoặc {@code debitRate} — không phải để che field thiếu dữ liệu.
     */
    private List<String> fallbackSources;

    /**
     * Bắt buộc phải có giá trị (sau khi đã thử fallback và default).
     * Field bắt buộc mà thiếu → ném {@link msb.com.vn.qrservice.mapping.declarative.FieldMappingException}
     * thay vì âm thầm bỏ qua hoặc dùng giá trị giả.
     */
    private boolean required = false;

    /**
     * Giá trị mặc định KHI field thực sự optional về nghiệp vụ (vd đơn vị tiền tệ mặc định VND).
     * KHÔNG dùng defaultValue cho field bắt buộc như mã giao dịch, số tham chiếu —
     * những field đó phải để {@code required: true} và không có default.
     */
    private String defaultValue;

    /** Cắt chuỗi nếu vượt độ dài quy định trong swagger, tránh backend trả lỗi validation */
    private Integer maxLength;

    /** Loại bỏ khoảng trắng đầu/cuối trước khi xử lý. Mặc định true. */
    private boolean trim = true;
}
