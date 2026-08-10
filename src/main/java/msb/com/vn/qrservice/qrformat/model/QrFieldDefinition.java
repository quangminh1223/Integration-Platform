package msb.com.vn.qrservice.qrformat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Định nghĩa một field trong chuẩn QR.
 *
 * <p>Cùng một định nghĩa được các generator hiểu khác nhau tùy {@link QrFormatType}:</p>
 * <ul>
 *   <li><b>EMV_TLV</b>: {@code tag} là tag TLV (vd "00", "38"); {@code children} cho field lồng nhau.</li>
 *   <li><b>KEY_VALUE</b>: {@code tag} là key xuất ra (vd "S" cho SSID).</li>
 *   <li><b>DELIMITED</b>: chỉ dùng thứ tự ({@code order}); {@code tag} bỏ qua.</li>
 *   <li><b>TEMPLATE</b>: {@code key} là tên placeholder trong template.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrFieldDefinition {

    /** Tên logic của field — khớp với key trong dữ liệu đầu vào (data map). */
    private String key;

    /** Tag/khóa xuất ra cho format type tương ứng (EMV tag, KEY_VALUE key...). */
    private String tag;

    /** Thứ tự xuất hiện trong nội dung. Số nhỏ ra trước. */
    @Builder.Default
    private int order = 0;

    /** Bắt buộc phải có giá trị (từ data hoặc default/fixed). */
    @Builder.Default
    private boolean required = false;

    /** Giá trị cố định — luôn dùng giá trị này, bỏ qua dữ liệu đầu vào. */
    private String fixedValue;

    /** Giá trị mặc định khi data đầu vào không cung cấp. */
    private String defaultValue;

    /** Độ dài tối đa của value (cắt bớt nếu vượt). null = không giới hạn. */
    private Integer maxLength;

    /** Độ dài tối thiểu của value (validate). null = không kiểm tra. */
    private Integer minLength;

    /**
     * Field con (chỉ dùng cho EMV_TLV). Khi có children, value của field này
     * được build từ TLV của các children thay vì lấy trực tiếp từ data.
     */
    private List<QrFieldDefinition> children;
}
