package msb.com.vn.qrservice.qrformat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Định nghĩa một chuẩn QR (khai báo, không phụ thuộc code).
 *
 * <p>Đây là "format" mà người dùng đưa vào: mô tả chuẩn gồm những field nào,
 * mã hóa kiểu gì, có CRC không. Từ định nghĩa này + dữ liệu đầu vào,
 * {@code DynamicQrService} sinh ra nội dung mã QR theo đúng chuẩn.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrStandardDefinition {

    /** Mã định danh duy nhất của chuẩn (vd "VIETQR", "WIFI", "URL"). */
    private String id;

    /** Tên mô tả chuẩn. */
    private String name;

    /** Kiểu mã hóa nội dung. */
    private QrFormatType formatType;

    /** Danh sách field của chuẩn. */
    private List<QrFieldDefinition> fields;

    // ─── Cấu hình KEY_VALUE / DELIMITED ─────────────────────────────────────

    /** Ký tự phân tách giữa các cặp (KEY_VALUE) hoặc giá trị (DELIMITED). */
    @Builder.Default
    private String separator = "&";

    /** Ký tự nối giữa key và value (chỉ KEY_VALUE), vd "=" hoặc ":". */
    @Builder.Default
    private String keyValueDelimiter = "=";

    /** Chuỗi thêm vào đầu nội dung (vd "WIFI:" cho chuẩn WIFI). */
    private String prefix;

    /** Chuỗi thêm vào cuối nội dung. */
    private String suffix;

    // ─── Cấu hình TEMPLATE ──────────────────────────────────────────────────

    /** Template chuỗi với placeholder {key} (chỉ dùng cho TEMPLATE). */
    private String template;

    // ─── Cấu hình RAW ───────────────────────────────────────────────────────

    /** Tên field chứa nội dung thô (chỉ dùng cho RAW). */
    private String rawContentKey;

    // ─── Cấu hình CRC (EMV_TLV) ─────────────────────────────────────────────

    /** Bật tính CRC ở cuối nội dung (EMVCo CRC16-CCITT). */
    @Builder.Default
    private boolean crcEnabled = false;

    /** Tag của field CRC (vd "63" trong VietQR). */
    @Builder.Default
    private String crcTag = "63";
}
