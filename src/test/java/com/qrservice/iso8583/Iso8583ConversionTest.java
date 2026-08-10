package com.qrservice.iso8583;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericPackager;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone test: Convert JSON transfer request → ISO 8583 message
 * Không cần Spring context, không cần kết nối TCP.
 *
 * Output: logs/iso8583_conversion_test.log
 *
 * Run: chuột phải → Run As → Java Application
 *      hoặc: mvn exec:java -Dexec.mainClass="com.qrservice.iso8583.Iso8583ConversionTest"
 */
public class Iso8583ConversionTest {

    // ─── Input JSON data (từ API request) ────────────────────────────────────
    private static final Map<String, String> INPUT_JSON = new LinkedHashMap<>() {{
        put("creditAccount",  "80000002233");
        put("creditAmount",   "98000");
        put("creditCurrency", "VND");
        put("creditRate",     "10000000");
        put("debitAccount",   "VND1217000011000");
        put("debitAmount",    "98000");
        put("debitCurrency",  "VND");
        put("debitRate",      "10000000");
        put("description",    "-704869-test timeout esb ok");
        put("vatFee",         "0");
        put("serviceFee",     "0");
        put("feeOwn",         "");
    }};

    // ─── Config mặc định ─────────────────────────────────────────────────────
    private static final String INSTITUTION_ID = "970436";
    private static final String TERMINAL_ID    = "TERM0001";
    private static final String MERCHANT_ID    = "MERCHANT000001 ";
    private static final String LOG_FILE       = "logs/iso8583_conversion_test.log";

    private static final AtomicInteger STAN_COUNTER = new AtomicInteger(1);

    // ─── Currency map ─────────────────────────────────────────────────────────
    private static final Map<String, String> CURRENCY_MAP = Map.of(
            "VND", "704", "USD", "840", "EUR", "978"
    );

    public static void main(String[] args) throws Exception {
        // Tạo thư mục logs nếu chưa có
        Path logDir = Paths.get("logs");
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }

        try (PrintWriter log = new PrintWriter(new FileWriter(LOG_FILE, false))) {

            printHeader(log);

            // ── 1. In input JSON ──────────────────────────────────────────────
            printSection(log, "INPUT JSON (API Request)");
            INPUT_JSON.forEach((k, v) ->
                    log.printf("  %-20s : %s%n", k, v.isEmpty() ? "(empty)" : v));

            // ── 2. Load packager ──────────────────────────────────────────────
            printSection(log, "LOADING ISO 8583 PACKAGER");
            GenericPackager packager = loadPackager(log);

            // ── 3. Build ISOMsg ───────────────────────────────────────────────
            printSection(log, "BUILDING ISO 8583 MESSAGE");
            ISOMsg msg = buildMessage(packager, log);

            // ── 4. In field-by-field ──────────────────────────────────────────
            printSection(log, "ISO 8583 FIELD BREAKDOWN");
            printFields(msg, log);

            // ── 5. Pack → raw bytes ───────────────────────────────────────────
            printSection(log, "PACKED MESSAGE (HEX)");
            byte[] packed = msg.pack();
            String hex = ISOUtil.hexString(packed);
            log.println("  Length (bytes) : " + packed.length);
            log.println("  Hex dump       : " + hex);
            log.println();
            log.println("  Hex dump (formatted, 32 chars per line):");
            for (int i = 0; i < hex.length(); i += 32) {
                int end = Math.min(i + 32, hex.length());
                log.printf("    [%04d-%04d] %s%n", i / 2, end / 2 - 1, hex.substring(i, end));
            }

            // ── 6. Simulate 2-byte length prefix (NACChannel format) ──────────
            printSection(log, "WITH 2-BYTE LENGTH PREFIX (NACChannel / TCP)");
            byte[] withHeader = addLengthPrefix(packed);
            log.println("  Total length   : " + withHeader.length + " bytes");
            log.println("  Header (2 bytes): "
                    + ISOUtil.hexString(new byte[]{withHeader[0], withHeader[1]}));
            log.println("  Full hex       : " + ISOUtil.hexString(withHeader));

            // ── 7. Unpack verify ──────────────────────────────────────────────
            printSection(log, "UNPACK VERIFICATION (round-trip)");
            ISOMsg unpacked = new ISOMsg();
            unpacked.setPackager(packager);
            unpacked.unpack(packed);
            log.println("  MTI            : " + unpacked.getMTI());
            printFields(unpacked, log);

            // ── 8. Summary ────────────────────────────────────────────────────
            printSection(log, "CONVERSION SUMMARY");
            log.println("  Status         : SUCCESS");
            log.println("  Input fields   : " + INPUT_JSON.size());
            log.println("  ISO 8583 DEs   : " + countSetFields(msg));
            log.println("  Packed size    : " + packed.length + " bytes");
            log.println("  Log file       : " + Paths.get(LOG_FILE).toAbsolutePath());

            printFooter(log);
        }

        // In ra console để biết file log ở đâu
        System.out.println("=================================================");
        System.out.println("  Conversion complete!");
        System.out.println("  Log file: " + Paths.get(LOG_FILE).toAbsolutePath());
        System.out.println("=================================================");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build ISOMsg từ INPUT_JSON
    // ─────────────────────────────────────────────────────────────────────────
    private static ISOMsg buildMessage(GenericPackager packager, PrintWriter log)
            throws ISOException {

        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);

        LocalDateTime now = LocalDateTime.now();
        String stan = String.format("%06d", STAN_COUNTER.getAndIncrement());
        String rrn  = now.format(DateTimeFormatter.ofPattern("MMddHH")) + stan;

        // MTI
        msg.setMTI("0200");
        log.println("  MTI            : 0200 (Financial Transaction Request)");

        // DE 2 - PAN = creditAccount
        set(msg, log, 2,  "PAN (creditAccount)",              INPUT_JSON.get("creditAccount"));

        // DE 3 - Processing Code: 40=transfer, 00=from any, 00=to any
        set(msg, log, 3,  "Processing Code",                  "400000");

        // DE 4 - Transaction Amount (debitAmount, 12 digits zero-padded)
        set(msg, log, 4,  "Transaction Amount (debitAmount)",  formatAmount(INPUT_JSON.get("debitAmount")));

        // DE 6 - Cardholder Billing Amount (creditAmount)
        set(msg, log, 6,  "Billing Amount (creditAmount)",     formatAmount(INPUT_JSON.get("creditAmount")));

        // DE 11 - STAN
        set(msg, log, 11, "STAN (auto-generated)",             stan);

        // DE 12 - Local Time HHmmss
        set(msg, log, 12, "Local Time",                        now.format(DateTimeFormatter.ofPattern("HHmmss")));

        // DE 13 - Local Date MMdd
        set(msg, log, 13, "Local Date",                        now.format(DateTimeFormatter.ofPattern("MMdd")));

        // DE 28 - Transaction Fee Amount (serviceFee)
        String fee = INPUT_JSON.get("serviceFee");
        String feeVal = (fee == null || fee.isBlank() || "0".equals(fee.trim()))
                ? "C00000000"
                : "C" + String.format("%08d", Long.parseLong(fee.trim()));
        set(msg, log, 28, "Transaction Fee (serviceFee)",      feeVal);

        // DE 32 - Acquiring Institution ID
        set(msg, log, 32, "Acquiring Institution ID",          INSTITUTION_ID);

        // DE 37 - Retrieval Reference Number (12 chars)
        set(msg, log, 37, "Retrieval Reference Number",        rrn);

        // DE 41 - Terminal ID (8 chars)
        set(msg, log, 41, "Terminal ID",                       ISOUtil.padright(TERMINAL_ID, 8, ' '));

        // DE 42 - Merchant ID (15 chars)
        set(msg, log, 42, "Merchant ID",                       ISOUtil.padright(MERCHANT_ID, 15, ' '));

        // DE 43 - Card Acceptor Name/Location = description (max 40)
        String desc = INPUT_JSON.get("description");
        if (desc != null && desc.length() > 40) desc = desc.substring(0, 40);
        set(msg, log, 43, "Description (description)",         desc);

        // DE 49 - Transaction Currency Code (debitCurrency → ISO 4217)
        set(msg, log, 49, "Transaction Currency (debitCurrency)",
                CURRENCY_MAP.getOrDefault(INPUT_JSON.get("debitCurrency"), "704"));

        // DE 51 - Billing Currency Code (creditCurrency → ISO 4217)
        set(msg, log, 51, "Billing Currency (creditCurrency)",
                CURRENCY_MAP.getOrDefault(INPUT_JSON.get("creditCurrency"), "704"));

        // DE 102 - Account ID 1 = debitAccount
        set(msg, log, 102, "Account ID 1 (debitAccount)",      INPUT_JSON.get("debitAccount"));

        // DE 103 - Account ID 2 = creditAccount
        set(msg, log, 103, "Account ID 2 (creditAccount)",     INPUT_JSON.get("creditAccount"));

        return msg;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static GenericPackager loadPackager(PrintWriter log) throws ISOException {
        InputStream is = Iso8583ConversionTest.class
                .getClassLoader()
                .getResourceAsStream("iso8583/iso8583.xml");
        if (is == null) {
            throw new ISOException("Cannot find iso8583/iso8583.xml in classpath. "
                    + "Make sure src/main/resources/iso8583/iso8583.xml exists.");
        }
        GenericPackager packager = new GenericPackager(is);
        log.println("  Packager loaded : GenericPackager (iso8583/iso8583.xml)");
        return packager;
    }

    private static void set(ISOMsg msg, PrintWriter log, int de, String name, String value)
            throws ISOException {
        msg.set(de, value);
        log.printf("  DE %3d  %-40s = [%s]%n", de, name, value);
    }

    private static String formatAmount(String amount) {
        if (amount == null || amount.isBlank()) return "000000000000";
        try {
            return String.format("%012d", Long.parseLong(amount.trim()));
        } catch (NumberFormatException e) {
            return "000000000000";
        }
    }

    private static byte[] addLengthPrefix(byte[] data) {
        byte[] result = new byte[data.length + 2];
        result[0] = (byte) ((data.length >> 8) & 0xFF);
        result[1] = (byte) (data.length & 0xFF);
        System.arraycopy(data, 0, result, 2, data.length);
        return result;
    }

    private static void printFields(ISOMsg msg, PrintWriter log) throws ISOException {
        log.printf("  %-6s %-45s %s%n", "DE", "Name", "Value");
        log.println("  " + "-".repeat(80));
        log.printf("  %-6s %-45s %s%n", "MTI", "Message Type Indicator", msg.getMTI());
        for (int i = 1; i <= 128; i++) {
            if (msg.hasField(i)) {
                String val = msg.getString(i);
                log.printf("  DE %-3d %-45s [%s]%n", i, getFieldName(i), val);
            }
        }
    }

    private static int countSetFields(ISOMsg msg) {
        int count = 0;
        for (int i = 1; i <= 128; i++) {
            if (msg.hasField(i)) count++;
        }
        return count;
    }

    private static String getFieldName(int de) {
        return switch (de) {
            case 2  -> "Primary Account Number (PAN)";
            case 3  -> "Processing Code";
            case 4  -> "Transaction Amount";
            case 6  -> "Cardholder Billing Amount";
            case 11 -> "STAN";
            case 12 -> "Local Transaction Time";
            case 13 -> "Local Transaction Date";
            case 28 -> "Transaction Fee Amount";
            case 32 -> "Acquiring Institution ID";
            case 37 -> "Retrieval Reference Number";
            case 39 -> "Response Code";
            case 41 -> "Card Acceptor Terminal ID";
            case 42 -> "Card Acceptor ID Code";
            case 43 -> "Card Acceptor Name/Location";
            case 49 -> "Transaction Currency Code";
            case 51 -> "Billing Currency Code";
            case 102 -> "Account ID 1 (Debit)";
            case 103 -> "Account ID 2 (Credit)";
            default -> "DE " + de;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Log formatting
    // ─────────────────────────────────────────────────────────────────────────

    private static void printHeader(PrintWriter log) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.println("=".repeat(82));
        log.println("  ISO 8583 CONVERSION TEST LOG");
        log.println("  Generated at : " + ts);
        log.println("  Description  : Convert JSON transfer request → ISO 8583 (MTI 0200)");
        log.println("=".repeat(82));
        log.println();
    }

    private static void printSection(PrintWriter log, String title) {
        log.println();
        log.println("┌─ " + title + " " + "─".repeat(Math.max(0, 76 - title.length())));
    }

    private static void printFooter(PrintWriter log) {
        log.println();
        log.println("=".repeat(82));
        log.println("  END OF LOG");
        log.println("=".repeat(82));
    }
}
