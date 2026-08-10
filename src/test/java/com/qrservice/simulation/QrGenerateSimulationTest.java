package com.qrservice.simulation;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrservice.dto.request.GenerateVietQrRequest;
import com.qrservice.dto.response.VietQrData;
import com.qrservice.service.VietQrBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giả lập giao dịch tạo mã QR cho khách hàng:
 *   Tên:  minhbq
 *   SĐT:  1235566  (dùng làm số tài khoản)
 *
 * Test này KHÔNG cần Spring context, Oracle, Redis hay Kafka.
 * Chạy độc lập bằng: mvn test -pl . -Dtest=QrGenerateSimulationTest
 */
class QrGenerateSimulationTest {

    private VietQrBuilderService vietQrBuilderService;

    // Thư mục lưu ảnh QR output
    private static final String OUTPUT_DIR = "target/qr-output";

    @BeforeEach
    void setUp() {
        vietQrBuilderService = new VietQrBuilderService();
    }

    // =========================================================
    //  TEST CHÍNH: Giả lập tạo QR cho minhbq / 1235566
    // =========================================================

    @Test
    @DisplayName("Giả lập giao dịch: Tạo QR cho minhbq - SĐT 1235566")
    void simulateQrGeneration_minhbq() throws Exception {

        System.out.println("=".repeat(60));
        System.out.println("  GIẢ LẬP TẠO MÃ QR GIAO DỊCH");
        System.out.println("=".repeat(60));

        // ---- 1. Chuẩn bị thông tin giao dịch ----
        GenerateVietQrRequest request = new GenerateVietQrRequest();
        request.setBankBin("970436");           // Vietcombank BIN
        request.setBankAccount("1235566");      // SĐT làm số tài khoản
        request.setAccountName("MINHBQ");       // Tên khách hàng
        request.setAmount(new BigDecimal("50000"));
        request.setDescription("Thanh toan minhbq");
        request.setTransactionRef("TXN" + System.currentTimeMillis());

        System.out.println("\n[INPUT] Thông tin giao dịch:");
        System.out.println("  Tên tài khoản : " + request.getAccountName());
        System.out.println("  Số tài khoản  : " + request.getBankAccount());
        System.out.println("  Ngân hàng BIN : " + request.getBankBin() + " (Vietcombank)");
        System.out.println("  Số tiền       : " + request.getAmount() + " VND");
        System.out.println("  Mô tả         : " + request.getDescription());
        System.out.println("  Mã giao dịch  : " + request.getTransactionRef());

        // ---- 2. Build chuỗi VietQR ----
        String qrContent = vietQrBuilderService.buildVietQrContent(request);

        System.out.println("\n[STEP 1] Build VietQR content:");
        System.out.println("  Độ dài chuỗi  : " + qrContent.length() + " ký tự");
        System.out.println("  QR Content    : " + qrContent);

        // ---- 3. Kiểm tra CRC ----
        boolean crcValid = vietQrBuilderService.verifyCrc(qrContent);
        System.out.println("\n[STEP 2] Kiểm tra CRC-16:");
        System.out.println("  CRC hợp lệ    : " + (crcValid ? "✓ PASS" : "✗ FAIL"));
        assertThat(crcValid).as("CRC phải hợp lệ").isTrue();

        // ---- 4. Tạo ảnh QR (ZXing) ----
        String imageBase64 = generateQrImage(qrContent, 300, 300);
        byte[] imageBytes = Base64.getDecoder().decode(imageBase64);

        System.out.println("\n[STEP 3] Tạo ảnh QR (ZXing):");
        System.out.println("  Kích thước ảnh: " + imageBytes.length + " bytes");
        System.out.println("  Base64 length : " + imageBase64.length() + " ký tự");
        System.out.println("  Base64 preview: " + imageBase64.substring(0, 40) + "...");
        assertThat(imageBase64).isNotBlank();
        assertThat(imageBytes.length).isGreaterThan(100);

        // ---- 5. Lưu ảnh ra file để xem trực quan ----
        saveQrImage(imageBytes, "minhbq_qr.png");

        // ---- 6. Decode lại ảnh để verify ----
        String decodedContent = decodeQrFromImage(imageBase64);
        System.out.println("\n[STEP 4] Decode lại ảnh QR:");
        System.out.println("  Decoded content: " + decodedContent);
        assertThat(decodedContent).isEqualTo(qrContent);
        System.out.println("  Nội dung khớp  : ✓ PASS");

        // ---- 7. Parse VietQR content ----
        VietQrData parsedData = vietQrBuilderService.parseVietQrContent(decodedContent);

        System.out.println("\n[STEP 5] Phân tách VietQR:");
        System.out.println("  Bank BIN      : " + parsedData.getBankBin());
        System.out.println("  Số tài khoản  : " + parsedData.getBankAccount());
        System.out.println("  Tên TK        : " + parsedData.getAccountName());
        System.out.println("  Số tiền       : " + parsedData.getAmount() + " " + parsedData.getCurrency());
        System.out.println("  Mô tả         : " + parsedData.getDescription());
        System.out.println("  Mã GD         : " + parsedData.getTransactionRef());
        System.out.println("  Service code  : " + parsedData.getServiceCode());
        System.out.println("  Quốc gia      : " + parsedData.getCountryCode());

        // ---- 8. Assert kết quả parse ----
        assertThat(parsedData.getBankBin()).isEqualTo("970436");
        assertThat(parsedData.getBankAccount()).isEqualTo("1235566");
        assertThat(parsedData.getAccountName()).isEqualTo("MINHBQ");
        assertThat(parsedData.getAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(parsedData.getCurrency()).isEqualTo("VND");
        assertThat(parsedData.getCountryCode()).isEqualTo("VN");

        System.out.println("\n[RESULT] Tất cả assertions PASSED ✓");
        System.out.println("  Ảnh QR đã lưu tại: " + OUTPUT_DIR + "/minhbq_qr.png");
        System.out.println("=".repeat(60));
    }

    @Test
    @DisplayName("Giả lập QR không có số tiền (QR tĩnh) cho minhbq")
    void simulateStaticQr_minhbq() throws Exception {
        System.out.println("\n--- QR TĨNH (không có số tiền) ---");

        GenerateVietQrRequest request = new GenerateVietQrRequest();
        request.setBankBin("970436");
        request.setBankAccount("1235566");
        request.setAccountName("MINHBQ");
        // Không set amount → QR tĩnh (Point of Initiation = 11)

        String qrContent = vietQrBuilderService.buildVietQrContent(request);

        System.out.println("QR Content (static): " + qrContent);
        System.out.println("Chứa '0111' (static): " + qrContent.contains("0111"));

        assertThat(qrContent).contains("0111"); // method=11 → static
        assertThat(vietQrBuilderService.verifyCrc(qrContent)).isTrue();

        String imageBase64 = generateQrImage(qrContent, 300, 300);
        saveQrImage(Base64.getDecoder().decode(imageBase64), "minhbq_static_qr.png");

        VietQrData parsed = vietQrBuilderService.parseVietQrContent(qrContent);
        assertThat(parsed.getBankAccount()).isEqualTo("1235566");
        assertThat(parsed.getAmount()).isNull(); // không có số tiền

        System.out.println("Số tiền: " + parsed.getAmount() + " (null = QR tĩnh ✓)");
        System.out.println("Ảnh đã lưu: " + OUTPUT_DIR + "/minhbq_static_qr.png");
    }

    // =========================================================
    //  Helpers (không dùng Spring bean để test độc lập)
    // =========================================================

    private String generateQrImage(String content, int width, int height) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private String decodeQrFromImage(String imageBase64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(imageBase64);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return new MultiFormatReader().decode(bitmap, hints).getText();
    }

    private void saveQrImage(byte[] imageBytes, String filename) {
        try {
            Path dir = Paths.get(OUTPUT_DIR);
            Files.createDirectories(dir);
            Path filePath = dir.resolve(filename);
            Files.write(filePath, imageBytes);
            System.out.println("  → Đã lưu ảnh: " + filePath.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("  → Không thể lưu ảnh: " + e.getMessage());
        }
    }
}
