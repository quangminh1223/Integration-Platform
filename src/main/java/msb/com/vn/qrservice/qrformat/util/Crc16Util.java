package msb.com.vn.qrservice.qrformat.util;

import java.nio.charset.StandardCharsets;

/**
 * CRC16-CCITT (False) — chuẩn dùng bởi EMVCo / VietQR.
 *
 * <p>Polynomial 0x1021, giá trị khởi tạo 0xFFFF, không reflect, kết quả HEX 4 ký tự.</p>
 */
public final class Crc16Util {

    private Crc16Util() {
    }

    /** Tính CRC16-CCITT của chuỗi, trả về HEX uppercase 4 ký tự. */
    public static String crc16Ccitt(String data) {
        int crc = 0xFFFF;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & 0x8000) != 0) ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
