package msb.com.vn.qrservice.iso8583.util;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiện ích xử lý ISOMsg dùng chung cho luồng inbound/outbound.
 */
@Slf4j
public final class IsoMessageUtils {

    private static final DateTimeFormatter TRANSMISSION_FMT = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter LOCAL_TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter LOCAL_DATE_FMT = DateTimeFormatter.ofPattern("MMdd");

    /** Field 11 — System Trace Audit Number, dùng làm khóa correlate request/response */
    public static final int FIELD_STAN = 11;
    /** Field 39 — Response Code */
    public static final int FIELD_RESPONSE_CODE = 39;
    /** Field 7 — Transmission Date and Time */
    public static final int FIELD_TRANSMISSION_DATETIME = 7;
    /** Field 12 — Local Transaction Time */
    public static final int FIELD_LOCAL_TIME = 12;
    /** Field 13 — Local Transaction Date */
    public static final int FIELD_LOCAL_DATE = 13;
    /** Field 37 — Retrieval Reference Number */
    public static final int FIELD_RRN = 37;
    /** Field 70 — Network Management Information Code */
    public static final int FIELD_NMI_CODE = 70;

    private IsoMessageUtils() {
    }

    /**
     * Suy ra MTI response từ MTI request: tăng chữ số thứ 3.
     * 0200→0210, 0100→0110, 0400→0410, 0800→0810.
     */
    public static String deriveResponseMti(String requestMti) {
        if (requestMti == null || requestMti.length() != 4) {
            throw new IllegalArgumentException("MTI không hợp lệ: " + requestMti);
        }
        char third = requestMti.charAt(2);
        if (third < '0' || third > '8') {
            throw new IllegalArgumentException("Chữ số thứ 3 của MTI không hợp lệ: " + requestMti);
        }
        return requestMti.substring(0, 2) + (char) (third + 1) + requestMti.charAt(3);
    }

    /**
     * Lấy MTI an toàn, trả về null nếu không đọc được.
     */
    public static String safeGetMti(ISOMsg msg) {
        try {
            return msg.getMTI();
        } catch (ISOException e) {
            log.warn("Không đọc được MTI: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Lấy STAN (field 11) — khóa correlate. Trả về null nếu không có.
     */
    public static String getStan(ISOMsg msg) {
        return msg.hasField(FIELD_STAN) ? msg.getString(FIELD_STAN) : null;
    }

    /**
     * Điền các field thời gian chuẩn nếu chưa có: 7, 12, 13.
     */
    public static void fillTimeFields(ISOMsg msg) throws ISOException {
        LocalDateTime now = LocalDateTime.now();
        if (!msg.hasField(FIELD_TRANSMISSION_DATETIME)) {
            msg.set(FIELD_TRANSMISSION_DATETIME, now.format(TRANSMISSION_FMT));
        }
        if (!msg.hasField(FIELD_LOCAL_TIME)) {
            msg.set(FIELD_LOCAL_TIME, now.format(LOCAL_TIME_FMT));
        }
        if (!msg.hasField(FIELD_LOCAL_DATE)) {
            msg.set(FIELD_LOCAL_DATE, now.format(LOCAL_DATE_FMT));
        }
    }

    /**
     * Trích toàn bộ field 1..128 thành map để log/trả về API.
     */
    public static Map<Integer, String> toFieldMap(ISOMsg msg) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        for (int i = 1; i <= 128; i++) {
            if (msg.hasField(i)) {
                String value = msg.getString(i);
                if (value != null) {
                    fields.put(i, value);
                }
            }
        }
        return fields;
    }

    /**
     * Dump gọn bản tin cho log: MTI + các field chính.
     */
    public static String summarize(ISOMsg msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("MTI=").append(safeGetMti(msg));
        appendIfPresent(sb, msg, 3, "PC");
        appendIfPresent(sb, msg, 4, "AMT");
        appendIfPresent(sb, msg, FIELD_STAN, "STAN");
        appendIfPresent(sb, msg, FIELD_RRN, "RRN");
        appendIfPresent(sb, msg, FIELD_RESPONSE_CODE, "RC");
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, ISOMsg msg, int field, String label) {
        if (msg.hasField(field)) {
            sb.append(' ').append(label).append('=').append(msg.getString(field));
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        String clean = hex.replaceAll("\\s+", "");
        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string phải có độ dài chẵn: " + clean.length());
        }
        byte[] bytes = new byte[clean.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /**
     * Bản tin ASCII in ra dạng đọc được (thay ký tự không in được bằng '.').
     */
    public static String toPrintableAscii(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            sb.append(c >= 32 && c < 127 ? c : '.');
        }
        return sb.toString();
    }
}
