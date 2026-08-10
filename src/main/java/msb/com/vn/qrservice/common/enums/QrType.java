package msb.com.vn.qrservice.common.enums;

/**
 * Loại mã QR được hỗ trợ
 */
public enum QrType {
    VIET_QR,    // Chuẩn VietQR (NAPAS)
    TEXT,       // QR chứa văn bản thuần
    URL,        // QR chứa URL
    CUSTOM      // QR tùy chỉnh
}
