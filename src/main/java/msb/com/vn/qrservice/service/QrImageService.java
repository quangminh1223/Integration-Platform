package msb.com.vn.qrservice.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import msb.com.vn.qrservice.common.exception.QrGenerationException;
import msb.com.vn.qrservice.common.exception.QrParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
public class QrImageService {

    @Value("${qr.image.width:300}")
    private int defaultWidth;

    @Value("${qr.image.height:300}")
    private int defaultHeight;

    @Value("${qr.image.format:PNG}")
    private String imageFormat;

    @Value("${qr.image.margin:1}")
    private int margin;

    public String generateQrBase64(String content, Integer width, Integer height) {
        int w = (width != null && width > 0) ? width : defaultWidth;
        int h = (height != null && height > 0) ? height : defaultHeight;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, margin);
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, w, h, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, imageFormat, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            log.debug("Tạo QR thành công, kích thước ảnh: {} bytes", imageBytes.length);
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (WriterException e) {
            throw new QrGenerationException("Lỗi khi tạo mã QR: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new QrGenerationException("Lỗi không xác định khi tạo QR: " + e.getMessage(), e);
        }
    }

    public String decodeQrFromBase64(String imageBase64) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                throw new QrParseException("Không thể đọc ảnh QR, định dạng không hợp lệ");
            }
            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            Result result = new MultiFormatReader().decode(bitmap, hints);
            log.debug("Đọc QR từ ảnh thành công: {}", result.getText());
            return result.getText();
        } catch (NotFoundException e) {
            throw new QrParseException("Không tìm thấy mã QR trong ảnh");
        } catch (QrParseException e) {
            throw e;
        } catch (Exception e) {
            throw new QrParseException("Lỗi khi đọc ảnh QR: " + e.getMessage(), e);
        }
    }
}
