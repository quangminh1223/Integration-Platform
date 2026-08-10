import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Giả lập (standalone, chỉ dùng JDK) luồng:
 *   Tạo VietQR  →  build request body API createMsbAccountTransfer  →  response mẫu.
 *
 * Tái hiện trung thực:
 *   - VietQrBuilderService.buildVietQrContent (TLV + CRC16)
 *   - VietQrTransferService.buildTransferPayload (mapping VietQR → swagger body)
 *   - VietQrTransferService.buildTransferResponse (mapping response → output)
 *
 * KHÔNG cần Maven / Spring / Oracle / Redis. Chạy bằng:
 *   java qr-sim/QrTransferSimulation.java
 *
 * Kết quả ghi ra: target/qr-output/
 *   - createMsbAccountTransfer.request.json   (body gửi sang backend)
 *   - createMsbAccountTransfer.response.json  (body backend trả về - mẫu)
 *   - createMsbAccountTransfer.io.txt         (log dễ đọc: QR + req + resp + output)
 */
public class QrTransferSimulation {

    // ─── Hằng số VietQR (giống VietQrBuilderService) ──────────────────────────
    static final String NAPAS_GUID = "A000000727";
    static final String SERVICE_CODE_TRANSFER = "QRIBFTTA";
    static final String CURRENCY_VND = "704";
    static final String COUNTRY_CODE = "VN";

    static final String OUTPUT_DIR = "target/qr-output";

    public static void main(String[] args) throws Exception {
        // ── 1. Thông tin giao dịch đầu vào (giả lập khách hàng) ───────────────
        String bankBin      = "970436";              // Vietcombank
        String bankAccount  = "1235566";
        String accountName  = "MINHBQ";
        BigInteger amount   = new BigInteger("50000");
        String description  = "Thanh toan minhbq";
        String transactionRef = "TXN" + System.currentTimeMillis();

        // Thông tin bổ sung cho transfer (bên gửi)
        String debitAccount = "0011002233445";
        String tranCode     = "QRTRF001";
        String channel      = "MOBILE";

        // ── 2. Build chuỗi VietQR ─────────────────────────────────────────────
        String qrContent = buildVietQrContent(bankBin, bankAccount, accountName,
                amount, description, transactionRef);
        boolean crcValid = verifyCrc(qrContent);            // copy y nguyen logic goc
        boolean crcValidFixed = verifyCrcFixed(qrContent);  // logic dung
        System.out.println(">>> verifyCrc (logic goc, them thua '6304') = " + crcValid);
        System.out.println(">>> verifyCrc (logic dung)                  = " + crcValidFixed);

        // ── 3. Build REQUEST body createMsbAccountTransfer ────────────────────
        Map<String, Object> request = buildTransferPayload(bankAccount, accountName, amount,
                description, transactionRef, debitAccount, tranCode, channel);

        // ── 4. Tạo RESPONSE mẫu (theo schema MsbAccountTransferResponse) ──────
        Map<String, Object> response = buildSampleResponse(debitAccount, bankAccount, amount,
                tranCode, transactionRef, description);

        // ── 5. Map response → output đơn giản (giống buildTransferResponse) ───
        Map<String, Object> output = buildMappedOutput(response, bankAccount, bankBin,
                debitAccount, amount, description);

        // ── 6. Ghi file ───────────────────────────────────────────────────────
        Path dir = Paths.get(OUTPUT_DIR);
        Files.createDirectories(dir);

        String reqJson  = toPrettyJson(request);
        String respJson = toPrettyJson(response);
        String outJson  = toPrettyJson(output);

        write(dir.resolve("createMsbAccountTransfer.request.json"), reqJson);
        write(dir.resolve("createMsbAccountTransfer.response.json"), respJson);

        StringBuilder io = new StringBuilder();
        String line = "=".repeat(78);
        io.append(line).append("\n");
        io.append("  GIA LAP: Tao QR -> goi API createMsbAccountTransfer (req/resp)\n");
        io.append("  Thoi gian: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        io.append(line).append("\n\n");

        io.append("[INPUT] Thong tin giao dich\n");
        io.append("  Ten tai khoan thu huong : ").append(accountName).append("\n");
        io.append("  So tai khoan thu huong  : ").append(bankAccount).append("\n");
        io.append("  Ngan hang BIN           : ").append(bankBin).append(" (Vietcombank)\n");
        io.append("  So tien                 : ").append(amount).append(" VND\n");
        io.append("  Noi dung                : ").append(description).append("\n");
        io.append("  Ma tham chieu           : ").append(transactionRef).append("\n");
        io.append("  TK ghi no (ben gui)     : ").append(debitAccount).append("\n");
        io.append("  Ma giao dich (tranCode) : ").append(tranCode).append("\n");
        io.append("  Kenh (channel)          : ").append(channel).append("\n\n");

        io.append("[STEP 1] VietQR content (TLV + CRC16)\n");
        io.append("  Do dai : ").append(qrContent.length()).append(" ky tu\n");
        io.append("  CRC     : ").append(crcValid ? "HOP LE" : "SAI").append("\n");
        io.append("  Chuoi   : ").append(qrContent).append("\n\n");

        io.append("[STEP 2] REQUEST body -> POST /party/msb/transfer/accttrf/create\n");
        io.append("  operationId = createMsbAccountTransfer\n");
        io.append(indent(reqJson)).append("\n\n");

        io.append("[STEP 3] RESPONSE body (mau - theo schema MsbAccountTransferResponse)\n");
        io.append(indent(respJson)).append("\n\n");

        io.append("[STEP 4] OUTPUT da map tra ve cho caller\n");
        io.append(indent(outJson)).append("\n");
        io.append(line).append("\n");

        write(dir.resolve("createMsbAccountTransfer.io.txt"), io.toString());

        // ── 7. In ra console ──────────────────────────────────────────────────
        System.out.println(io);
        System.out.println("Da ghi file vao: " + dir.toAbsolutePath());
        System.out.println("  - createMsbAccountTransfer.request.json");
        System.out.println("  - createMsbAccountTransfer.response.json");
        System.out.println("  - createMsbAccountTransfer.io.txt");
    }

    // ─── VietQR build (faithful copy of VietQrBuilderService) ─────────────────

    static String buildVietQrContent(String bankBin, String bankAccount, String accountName,
                                     BigInteger amount, String description, String transactionRef) {
        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));
        sb.append(tlv("01", amount != null ? "12" : "11"));
        sb.append(tlv("38", buildAccountInfo(bankBin, bankAccount)));
        sb.append(tlv("52", "0000"));
        sb.append(tlv("53", CURRENCY_VND));
        if (amount != null && amount.signum() > 0) {
            sb.append(tlv("54", amount.toString()));
        }
        sb.append(tlv("58", COUNTRY_CODE));
        String name = (accountName != null && !accountName.isBlank()) ? accountName : "NGUOI DUNG";
        sb.append(tlv("59", name));
        sb.append(tlv("60", "VIET NAM"));
        String additional = buildAdditionalData(description, transactionRef);
        if (!additional.isEmpty()) {
            sb.append(tlv("62", additional));
        }
        sb.append("6304");
        sb.append(calculateCrc16(sb.toString()));
        return sb.toString();
    }

    static String buildAccountInfo(String bankBin, String bankAccount) {
        String guid = tlv("00", NAPAS_GUID);
        String binField = tlv("00", bankBin);
        String accountField = tlv("01", bankAccount);
        String serviceField = tlv("02", SERVICE_CODE_TRANSFER);
        String accountInfo = tlv("01", binField + accountField + serviceField);
        return guid + accountInfo;
    }

    static String buildAdditionalData(String description, String transactionRef) {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) {
            String desc = description.length() > 25 ? description.substring(0, 25) : description;
            sb.append(tlv("08", desc));
        }
        if (transactionRef != null && !transactionRef.isBlank()) {
            sb.append(tlv("05", transactionRef));
        }
        return sb.toString();
    }

    static String tlv(String tag, String value) {
        return tag + String.format("%02d", value.length()) + value;
    }

    static String calculateCrc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & 0x8000) != 0) ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    static boolean verifyCrc(String content) {
        if (content.length() < 4) return false;
        String body = content.substring(0, content.length() - 4);
        String expected = content.substring(content.length() - 4);
        return calculateCrc16(body + "6304").equalsIgnoreCase(expected);
    }

    /** Logic dung: body da chua "6304" roi, khong cong them. */
    static boolean verifyCrcFixed(String content) {
        if (content.length() < 4) return false;
        String body = content.substring(0, content.length() - 4);
        String expected = content.substring(content.length() - 4);
        return calculateCrc16(body).equalsIgnoreCase(expected);
    }

    // ─── Mapping VietQR -> createMsbAccountTransfer body ──────────────────────

    static Map<String, Object> buildTransferPayload(String bankAccount, String accountName,
                                                    BigInteger amount, String description,
                                                    String transactionRef, String debitAccount,
                                                    String tranCode, String channel) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("override", new LinkedHashMap<>());
        header.put("audit", new LinkedHashMap<>());
        root.put("header", header);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("debitCurrency", "VND");
        body.put("creditCurrency", "VND");
        body.put("msbTransCode", tranCode != null ? tranCode : "QRTRF001");
        body.put("msbTransSeq", transactionRef != null ? transactionRef
                : String.valueOf(System.currentTimeMillis() % 1_000_000_000L));

        if (debitAccount != null && !debitAccount.isBlank()) {
            body.put("debitAccount", debitAccount);
        }
        body.put("creditAccount", bankAccount);

        if (amount != null) {
            String amt = amount.toString();
            body.put("debitAmount", amt);
            body.put("creditAmount", amt);
        }

        if (description != null && !description.isBlank()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("paymentDetail", truncate(description, 50));
            List<Object> details = new ArrayList<>();
            details.add(detail);
            body.put("paymentDetails", details);
        }

        if (channel != null && !channel.isBlank()) {
            body.put("msbChannel", channel);
        }

        root.put("body", body);
        return root;
    }

    // ─── Response mau (schema MsbAccountTransferResponse) ─────────────────────

    static Map<String, Object> buildSampleResponse(String debitAccount, String creditAccount,
                                                   BigInteger amount, String tranCode,
                                                   String transactionRef, String description) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> header = new LinkedHashMap<>();
        String ftId = "FT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        header.put("id", ftId);
        header.put("status", "success");
        header.put("transactionStatus", "LIVE");
        header.put("uniqueIdentifier", UUID.randomUUID().toString());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("T24_time", 120);
        audit.put("versionNumber", "1");
        audit.put("requestParse_time", 5.2);
        audit.put("responseParse_time", 3.1);
        header.put("audit", audit);
        root.put("header", header);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("debitAccount", debitAccount);
        body.put("debitCurrency", "VND");
        body.put("creditAccount", creditAccount);
        body.put("creditCurrency", "VND");
        if (amount != null) {
            body.put("debitAmount", amount.toString());
            body.put("creditAmount", amount.toString());
        }
        body.put("msbTransCode", tranCode);
        body.put("msbTransSeq", transactionRef);
        if (description != null && !description.isBlank()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("paymentDetail", truncate(description, 50));
            List<Object> details = new ArrayList<>();
            details.add(detail);
            body.put("paymentDetails", details);
        }
        root.put("body", body);
        return root;
    }

    // ─── Map response -> output (giong buildTransferResponse) ─────────────────

    @SuppressWarnings("unchecked")
    static Map<String, Object> buildMappedOutput(Map<String, Object> response, String creditAccount,
                                                 String bankBin, String debitAccount,
                                                 BigInteger amount, String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> header = (Map<String, Object>) response.get("header");
        result.put("success", true);
        result.put("transactionId", header.get("id"));
        result.put("status", header.get("status"));
        result.put("debitAccount", debitAccount);
        result.put("creditAccount", creditAccount);
        result.put("amount", amount != null ? amount.toString() : "");
        result.put("currency", "VND");
        result.put("bankBin", bankBin);
        result.put("description", description);
        return result;
    }

    static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }

    // ─── Pretty JSON (JDK thuan, khong can Jackson) ───────────────────────────

    static String toPrettyJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        writeJson(obj, sb, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static void writeJson(Object obj, StringBuilder sb, int depth) {
        String pad = "  ".repeat(depth);
        String padIn = "  ".repeat(depth + 1);
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            if (map.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                sb.append(padIn).append('"').append(escape(e.getKey())).append("\": ");
                writeJson(e.getValue(), sb, depth + 1);
                if (++i < map.size()) sb.append(',');
                sb.append('\n');
            }
            sb.append(pad).append('}');
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            if (list.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(padIn);
                writeJson(list.get(i), sb, depth + 1);
                if (i < list.size() - 1) sb.append(',');
                sb.append('\n');
            }
            sb.append(pad).append(']');
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj);
        } else if (obj == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(obj.toString())).append('"');
        }
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String indent(String text) {
        StringBuilder sb = new StringBuilder();
        for (String l : text.split("\n", -1)) sb.append("  ").append(l).append("\n");
        return sb.toString().stripTrailing();
    }

    static void write(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
