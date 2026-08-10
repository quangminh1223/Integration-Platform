package msb.com.vn.qrservice.iso8583.codec;

/**
 * Kiểu length header đứng trước payload ISO8583 trên TCP stream.
 * ISO8583 không tự mô tả độ dài nên phải có header để tách bản tin.
 */
public enum LengthHeaderType {

    /** 2 byte nhị phân big-endian (phổ biến nhất, NAPAS/Visa/MasterCard) */
    BINARY_2(2),

    /** 4 byte nhị phân big-endian */
    BINARY_4(4),

    /** 4 ký tự ASCII, vd "0123" = 123 byte */
    ASCII_4(4),

    /** 2 byte BCD, vd 0x01 0x23 = 123 byte */
    BCD_2(2),

    /** Không có header — mỗi lần đọc là một bản tin (chỉ dùng cho test) */
    NONE(0);

    private final int headerSize;

    LengthHeaderType(int headerSize) {
        this.headerSize = headerSize;
    }

    public int getHeaderSize() {
        return headerSize;
    }
}
