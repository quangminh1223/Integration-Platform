package msb.com.vn.qrservice.iso8583.codec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Đọc/ghi frame ISO8583 trên TCP stream theo length-prefix.
 *
 * <p>Layout: [length header][payload ISO8583]</p>
 *
 * <p>Thread-safe: không giữ state, mọi thao tác dựa trên stream truyền vào.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Iso8583FrameCodec {

    private final Iso8583SocketProperties properties;

    /**
     * Đọc một frame từ stream.
     *
     * @return payload ISO8583 dạng byte, hoặc {@code null} nếu peer đã đóng kết nối
     * @throws IOException lỗi đọc socket hoặc frame không hợp lệ
     */
    public byte[] readFrame(DataInputStream in) throws IOException {
        Iso8583SocketProperties.Framing framing = properties.getFraming();
        LengthHeaderType type = framing.getHeaderType();

        if (type == LengthHeaderType.NONE) {
            byte[] buffer = new byte[framing.getMaxMessageLength()];
            int read = in.read(buffer);
            if (read <= 0) {
                return null;
            }
            byte[] payload = new byte[read];
            System.arraycopy(buffer, 0, payload, 0, read);
            return payload;
        }

        byte[] header = new byte[type.getHeaderSize()];
        try {
            in.readFully(header);
        } catch (EOFException eof) {
            return null; // peer đóng kết nối bình thường
        }

        int length = decodeLength(header, type);

        if (framing.isLengthIncludesHeader()) {
            length -= type.getHeaderSize();
        }

        if (length <= 0) {
            throw new IOException("Length header không hợp lệ: " + length);
        }
        if (length > framing.getMaxMessageLength()) {
            throw new IOException("Bản tin vượt giới hạn: " + length
                    + " > " + framing.getMaxMessageLength() + " byte");
        }

        byte[] payload = new byte[length];
        in.readFully(payload);
        return payload;
    }

    /**
     * Ghi một frame ra stream (có flush).
     */
    public void writeFrame(OutputStream out, byte[] payload) throws IOException {
        Iso8583SocketProperties.Framing framing = properties.getFraming();
        LengthHeaderType type = framing.getHeaderType();

        if (payload.length > framing.getMaxMessageLength()) {
            throw new IOException("Bản tin gửi vượt giới hạn: " + payload.length
                    + " > " + framing.getMaxMessageLength() + " byte");
        }

        if (type != LengthHeaderType.NONE) {
            int declared = framing.isLengthIncludesHeader()
                    ? payload.length + type.getHeaderSize()
                    : payload.length;
            out.write(encodeLength(declared, type));
        }

        out.write(payload);
        out.flush();
    }

    // ─── Encode / decode length ─────────────────────────────────────────────

    private int decodeLength(byte[] header, LengthHeaderType type) throws IOException {
        return switch (type) {
            case BINARY_2 -> ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
            case BINARY_4 -> ((header[0] & 0xFF) << 24)
                    | ((header[1] & 0xFF) << 16)
                    | ((header[2] & 0xFF) << 8)
                    | (header[3] & 0xFF);
            case ASCII_4 -> parseAsciiLength(header);
            case BCD_2 -> parseBcdLength(header);
            case NONE -> throw new IOException("NONE không có length header");
        };
    }

    private byte[] encodeLength(int length, LengthHeaderType type) throws IOException {
        return switch (type) {
            case BINARY_2 -> new byte[]{
                    (byte) ((length >> 8) & 0xFF),
                    (byte) (length & 0xFF)
            };
            case BINARY_4 -> new byte[]{
                    (byte) ((length >> 24) & 0xFF),
                    (byte) ((length >> 16) & 0xFF),
                    (byte) ((length >> 8) & 0xFF),
                    (byte) (length & 0xFF)
            };
            case ASCII_4 -> String.format("%04d", length).getBytes(StandardCharsets.US_ASCII);
            case BCD_2 -> new byte[]{
                    (byte) (((length / 1000 % 10) << 4) | (length / 100 % 10)),
                    (byte) (((length / 10 % 10) << 4) | (length % 10))
            };
            case NONE -> throw new IOException("NONE không có length header");
        };
    }

    private int parseAsciiLength(byte[] header) throws IOException {
        String text = new String(header, StandardCharsets.US_ASCII);
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IOException("ASCII length header không hợp lệ: '" + text + "'");
        }
    }

    private int parseBcdLength(byte[] header) throws IOException {
        int length = 0;
        for (byte b : header) {
            int high = (b >> 4) & 0x0F;
            int low = b & 0x0F;
            if (high > 9 || low > 9) {
                throw new IOException("BCD length header không hợp lệ");
            }
            length = length * 100 + high * 10 + low;
        }
        return length;
    }
}
