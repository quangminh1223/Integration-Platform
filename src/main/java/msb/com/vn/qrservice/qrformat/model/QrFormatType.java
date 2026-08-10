package msb.com.vn.qrservice.qrformat.model;

/**
 * Kiểu mã hóa nội dung của một chuẩn QR.
 *
 * <p>Mỗi kiểu tương ứng với một {@code QrContentGenerator} cụ thể, quyết định
 * cách các field được kết hợp thành chuỗi nội dung cuối cùng của mã QR.</p>
 */
public enum QrFormatType {

    /**
     * EMVCo TLV (Tag-Length-Value) — dùng cho VietQR / EMVCo Merchant QR.
     * Mỗi field xuất ra dạng {@code tag + length(2 chữ số) + value}, hỗ trợ field lồng nhau,
     * và có thể tính CRC ở cuối.
     */
    EMV_TLV,

    /**
     * Cặp key=value nối với nhau bằng một separator (vd: {@code &} hoặc xuống dòng).
     * Phù hợp cho QR kiểu query-string hoặc cấu hình WIFI.
     */
    KEY_VALUE,

    /**
     * Các giá trị nối với nhau bằng một delimiter theo đúng thứ tự field.
     * Phù hợp cho các chuẩn dạng phân tách cố định (vd: CSV-like).
     */
    DELIMITED,

    /**
     * Template chuỗi với placeholder dạng {@code {key}} được thay bằng giá trị field.
     * Phù hợp cho URL, deep-link, hoặc định dạng văn bản tùy biến.
     */
    TEMPLATE,

    /**
     * Lấy nguyên một field nội dung duy nhất làm payload (không biến đổi).
     */
    RAW
}
