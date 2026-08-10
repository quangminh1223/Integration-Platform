/**
 * Script test tao ma QR cho minhbq / 123456
 * Chay bang: java TestQrGenerate.java  (Java 21, khong can Maven)
 *
 * Logic VietQR EMVCo copy tu VietQrBuilderService
 */
public class TestQrGenerate {

    static final String NAPAS_GUID   = "A000000727";
    static final String SERVICE_CODE = "QRIBFTTA";
    static final String CURRENCY_VND = "704";
    static final String COUNTRY_CODE = "VN";

    public static void main(String[] args) {

        System.out.println("=".repeat(65));
        System.out.println("  TEST TAO MA QR - THONG TIN KHACH HANG");
        System.out.println("=".repeat(65));

        // ---- Thong tin giao dich ----
        String accountName = "MINHBQ";
        String bankAccount = "123456";
        String bankBin     = "970436";   // Vietcombank
        String amount      = "50000";    // 50,000 VND
        String description = "Thanh toan minhbq";
        String txnRef      = "TXN" + System.currentTimeMillis();

        System.out.println("\n[INPUT] Thong tin giao dich:");
        System.out.printf("  %-20s: %s%n",  "Ten tai khoan",  accountName);
        System.out.printf("  %-20s: %s%n",  "So tai khoan",   bankAccount);
        System.out.printf("  %-20s: %s (Vietcombank)%n", "Bank BIN", bankBin);
        System.out.printf("  %-20s: %s VND%n", "So tien",     amount);
        System.out.printf("  %-20s: %s%n",  "Mo ta",          description);
        System.out.printf("  %-20s: %s%n",  "Ma giao dich",   txnRef);

        // ---- STEP 1: Build VietQR content ----
        System.out.println("\n[STEP 1] Build chuoi VietQR (EMVCo format)...");
        String qrContent = buildVietQr(bankBin, bankAccount, accountName, amount, description, txnRef);
        System.out.println("  QR Content  : " + qrContent);
        System.out.println("  Do dai      : " + qrContent.length() + " ky tu");

        // ---- STEP 2: Verify CRC ----
        System.out.println("\n[STEP 2] Kiem tra CRC-16/CCITT...");
        boolean crcOk = verifyCrc(qrContent);
        System.out.println("  CRC hop le  : " + (crcOk ? "PASS [OK]" : "FAIL [X]"));

        // ---- STEP 3: Parse lai de verify ----
        System.out.println("\n[STEP 3] Phan tach (parse) chuoi VietQR...");
        ParseResult parsed = parse(qrContent);
        System.out.printf("  %-20s: %s%n", "Bank BIN",      parsed.bankBin);
        System.out.printf("  %-20s: %s%n", "So tai khoan",  parsed.bankAccount);
        System.out.printf("  %-20s: %s%n", "Ten tai khoan", parsed.accountName);
        System.out.printf("  %-20s: %s %s%n", "So tien",
                parsed.amount.isEmpty() ? "(khong co)" : parsed.amount, parsed.currency);
        System.out.printf("  %-20s: %s%n", "Quoc gia",      parsed.country);
        System.out.printf("  %-20s: %s%n", "Mo ta",         parsed.description);
        System.out.printf("  %-20s: %s%n", "Ma giao dich",  parsed.txnRef);

        // ---- STEP 4: Assert ----
        System.out.println("\n[STEP 4] Assertions...");
        assertEqual("Bank BIN",      "970436",  parsed.bankBin);
        assertEqual("So tai khoan",  "123456",  parsed.bankAccount);
        assertEqual("Ten tai khoan", "MINHBQ",  parsed.accountName);
        assertEqual("So tien",       "50000",   parsed.amount);
        assertEqual("Currency",      "VND",     parsed.currency);
        assertEqual("Quoc gia",      "VN",      parsed.country);
        assertEqual("CRC",           "true",    String.valueOf(crcOk));

        // ---- STEP 5: Simulate HTTP Response ----
        System.out.println("\n[STEP 5] Gia lap HTTP Response tu API:");
        String fakeId = java.util.UUID.randomUUID().toString();
        String now    = java.time.LocalDateTime.now().toString();
        System.out.println("  POST /api/v1/qr/generate/vietqr");
        System.out.println("  HTTP 201 Created");
        System.out.println("  {");
        System.out.println("    \"success\": true,");
        System.out.println("    \"code\": \"SUCCESS\",");
        System.out.println("    \"message\": \"Tao ma VietQR thanh cong\",");
        System.out.println("    \"data\": {");
        System.out.println("      \"id\": \"" + fakeId + "\",");
        System.out.println("      \"qrType\": \"VIET_QR\",");
        System.out.println("      \"bankBin\": \"" + bankBin + "\",");
        System.out.println("      \"bankAccount\": \"" + bankAccount + "\",");
        System.out.println("      \"accountName\": \"" + accountName + "\",");
        System.out.println("      \"amount\": " + amount + ",");
        System.out.println("      \"currency\": \"VND\",");
        System.out.println("      \"txnRef\": \"" + txnRef + "\",");
        System.out.println("      \"qrContent\": \"" + qrContent + "\",");
        System.out.println("      \"imageBase64\": \"<base64_png_image_300x300>\",");
        System.out.println("      \"createdAt\": \"" + now + "\"");
        System.out.println("    }");
        System.out.println("  }");

        // ---- STEP 6: QR tinh (khong co so tien) ----
        System.out.println("\n[STEP 6] QR tinh (khong co so tien - static QR):");
        String staticQr = buildVietQr(bankBin, bankAccount, accountName, null, null, null);
        boolean staticCrc = verifyCrc(staticQr);
        ParseResult staticParsed = parse(staticQr);
        System.out.println("  QR Content  : " + staticQr);
        System.out.println("  CRC hop le  : " + (staticCrc ? "PASS [OK]" : "FAIL [X]"));
        System.out.println("  So tien     : " + (staticParsed.amount.isEmpty() ? "(null - static QR) [OK]" : staticParsed.amount));
        System.out.println("  Method=11   : " + (staticQr.contains("0111") ? "PASS [OK]" : "FAIL [X]"));

        System.out.println("\n" + "=".repeat(65));
        System.out.println("  KET QUA: TAT CA BUOC THANH CONG [OK]");
        System.out.println("=".repeat(65));
    }

    // ================================================================
    //  Build VietQR string theo chuan EMVCo / NAPAS
    // ================================================================
    static String buildVietQr(String bankBin, String bankAccount,
                               String accountName, String amount,
                               String description, String txnRef) {
        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));
        sb.append(tlv("01", amount != null ? "12" : "11"));
        sb.append(tlv("38", buildAccountInfo(bankBin, bankAccount)));
        sb.append(tlv("52", "0000"));
        sb.append(tlv("53", CURRENCY_VND));
        if (amount != null && !amount.isBlank()) {
            sb.append(tlv("54", amount));
        }
        sb.append(tlv("58", COUNTRY_CODE));
        String name = (accountName != null && !accountName.isBlank()) ? accountName : "NGUOI DUNG";
        sb.append(tlv("59", name));
        sb.append(tlv("60", "VIET NAM"));
        String addData = buildAdditionalData(description, txnRef);
        if (!addData.isEmpty()) {
            sb.append(tlv("62", addData));
        }
        // CRC: append "6304" truoc, tinh CRC tren toan bo chuoi do
        sb.append("6304");
        sb.append(crc16(sb.toString()));
        return sb.toString();
    }

    static String buildAccountInfo(String bankBin, String bankAccount) {
        String guid     = tlv("00", NAPAS_GUID);
        String binField = tlv("00", bankBin);
        String accField = tlv("01", bankAccount);
        String svcField = tlv("02", SERVICE_CODE);
        String nested   = tlv("01", binField + accField + svcField);
        return guid + nested;
    }

    static String buildAdditionalData(String description, String txnRef) {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) {
            String desc = description.length() > 25 ? description.substring(0, 25) : description;
            sb.append(tlv("08", desc));
        }
        if (txnRef != null && !txnRef.isBlank()) {
            sb.append(tlv("05", txnRef));
        }
        return sb.toString();
    }

    // ================================================================
    //  Parse VietQR string
    // ================================================================
    static class ParseResult {
        String bankBin = "", bankAccount = "", accountName = "",
               amount = "", currency = "", country = "",
               description = "", txnRef = "";
    }

    static ParseResult parse(String content) {
        ParseResult r = new ParseResult();
        int i = 0;
        while (i + 4 <= content.length() - 4) {
            String tag = content.substring(i, i + 2);
            int len;
            try { len = Integer.parseInt(content.substring(i + 2, i + 4)); }
            catch (NumberFormatException e) { break; }
            if (i + 4 + len > content.length()) break;
            String val = content.substring(i + 4, i + 4 + len);

            switch (tag) {
                case "38" -> parseAccountInfo(val, r);
                case "53" -> r.currency = "704".equals(val) ? "VND" : val;
                case "54" -> r.amount = val;
                case "58" -> r.country = val;
                case "59" -> r.accountName = val;
                case "62" -> parseAdditionalData(val, r);
            }
            i += 4 + len;
        }
        return r;
    }

    static void parseAccountInfo(String val, ParseResult r) {
        int j = 0;
        while (j + 4 <= val.length()) {
            String t = val.substring(j, j + 2);
            int l;
            try { l = Integer.parseInt(val.substring(j + 2, j + 4)); }
            catch (NumberFormatException e) { break; }
            if (j + 4 + l > val.length()) break;
            String v = val.substring(j + 4, j + 4 + l);
            if ("01".equals(t)) parseNestedAccountInfo(v, r);
            j += 4 + l;
        }
    }

    static void parseNestedAccountInfo(String val, ParseResult r) {
        int k = 0;
        while (k + 4 <= val.length()) {
            String t = val.substring(k, k + 2);
            int l;
            try { l = Integer.parseInt(val.substring(k + 2, k + 4)); }
            catch (NumberFormatException e) { break; }
            if (k + 4 + l > val.length()) break;
            String v = val.substring(k + 4, k + 4 + l);
            if ("00".equals(t)) r.bankBin = v;
            if ("01".equals(t)) r.bankAccount = v;
            k += 4 + l;
        }
    }

    static void parseAdditionalData(String val, ParseResult r) {
        int j = 0;
        while (j + 4 <= val.length()) {
            String t = val.substring(j, j + 2);
            int l;
            try { l = Integer.parseInt(val.substring(j + 2, j + 4)); }
            catch (NumberFormatException e) { break; }
            if (j + 4 + l > val.length()) break;
            String v = val.substring(j + 4, j + 4 + l);
            if ("05".equals(t)) r.txnRef = v;
            if ("08".equals(t)) r.description = v;
            j += 4 + l;
        }
    }

    // ================================================================
    //  TLV helper
    // ================================================================
    static String tlv(String tag, String value) {
        return tag + String.format("%02d", value.length()) + value;
    }

    // ================================================================
    //  CRC-16/CCITT-FALSE
    // ================================================================
    static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    static boolean verifyCrc(String content) {
        if (content.length() < 8) return false;
        // Phan data = tat ca tru 4 ky tu CRC cuoi (bao gom ca "6304")
        String body     = content.substring(0, content.length() - 4);
        String expected = content.substring(content.length() - 4);
        return crc16(body).equalsIgnoreCase(expected);
    }

    // ================================================================
    //  Assert helper
    // ================================================================
    static void assertEqual(String field, String expected, String actual) {
        boolean ok = expected.equals(actual);
        System.out.printf("  %-20s: expected=%-10s actual=%-10s %s%n",
                field, expected, actual, ok ? "[OK]" : "[FAIL]");
        if (!ok) {
            throw new RuntimeException("ASSERTION FAILED: " + field
                    + " expected=" + expected + " actual=" + actual);
        }
    }
}
