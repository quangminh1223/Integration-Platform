import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Standalone ISO 8583 conversion test - KHÔNG cần Maven, KHÔNG cần jPOS.
 * Tự build bitmap + pack fields thủ công theo chuẩn ISO 8583 ASCII.
 *
 * Compile: javac Iso8583Test.java
 * Run    : java Iso8583Test
 */
public class Iso8583Test {

    // ── Input JSON ────────────────────────────────────────────────────────────
    static final Map<String, String> INPUT = new LinkedHashMap<>();
    static {
        INPUT.put("creditAccount",  "80000002233");
        INPUT.put("creditAmount",   "98000");
        INPUT.put("creditCurrency", "VND");
        INPUT.put("creditRate",     "10000000");
        INPUT.put("debitAccount",   "VND1217000011000");
        INPUT.put("debitAmount",    "98000");
        INPUT.put("debitCurrency",  "VND");
        INPUT.put("debitRate",      "10000000");
        INPUT.put("description",    "-704869-test timeout esb ok");
        INPUT.put("vatFee",         "0");
        INPUT.put("serviceFee",     "0");
        INPUT.put("feeOwn",         "");
    }

    // ── Currency map (ISO 4217 numeric) ───────────────────────────────────────
    static final Map<String, String> CCY = Map.of(
        "VND","704","USD","840","EUR","978","JPY","392"
    );

    static final String LOG_FILE = "logs/iso8583_conversion_test.log";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get("logs"));

        try (PrintWriter w = new PrintWriter(new FileWriter(LOG_FILE, false))) {
            LocalDateTime now = LocalDateTime.now();
            String stan = "000001";
            String rrn  = now.format(DateTimeFormatter.ofPattern("MMddHH")) + stan;

            // ── Build field map ───────────────────────────────────────────────
            // Key = DE number, Value = [name, rawValue, packedValue, type, length]
            Map<Integer, String[]> fields = new LinkedHashMap<>();

            // DE 2  - PAN (LLVAR numeric, max 19)
            String pan = INPUT.get("creditAccount");
            fields.put(2,  new String[]{"Primary Account Number (PAN)",
                pan, llvar(pan), "LLVAR-N", String.valueOf(pan.length())});

            // DE 3  - Processing Code (fixed 6N)
            fields.put(3,  new String[]{"Processing Code",
                "400000", "400000", "FIXED-N", "6"});

            // DE 4  - Transaction Amount (fixed 12N)
            String amt = fmtAmt(INPUT.get("debitAmount"));
            fields.put(4,  new String[]{"Transaction Amount (debitAmount)",
                INPUT.get("debitAmount"), amt, "FIXED-N", "12"});

            // DE 6  - Billing Amount (fixed 12N)
            String bamt = fmtAmt(INPUT.get("creditAmount"));
            fields.put(6,  new String[]{"Cardholder Billing Amount (creditAmount)",
                INPUT.get("creditAmount"), bamt, "FIXED-N", "12"});

            // DE 11 - STAN (fixed 6N)
            fields.put(11, new String[]{"STAN (auto-generated)",
                stan, stan, "FIXED-N", "6"});

            // DE 12 - Local Time (fixed 6N HHmmss)
            String ltime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
            fields.put(12, new String[]{"Local Transaction Time",
                ltime, ltime, "FIXED-N", "6"});

            // DE 13 - Local Date (fixed 4N MMdd)
            String ldate = now.format(DateTimeFormatter.ofPattern("MMdd"));
            fields.put(13, new String[]{"Local Transaction Date",
                ldate, ldate, "FIXED-N", "4"});

            // DE 28 - Transaction Fee Amount (fixed 9 ANS: C/D + 8N)
            String fee = "C00000000";
            fields.put(28, new String[]{"Transaction Fee Amount (serviceFee)",
                "0", fee, "FIXED-ANS", "9"});

            // DE 32 - Acquiring Institution ID (LLVAR numeric, max 11)
            String instId = "970436";
            fields.put(32, new String[]{"Acquiring Institution ID",
                instId, llvar(instId), "LLVAR-N", String.valueOf(instId.length())});

            // DE 37 - Retrieval Reference Number (fixed 12 ANS)
            fields.put(37, new String[]{"Retrieval Reference Number",
                rrn, rrn, "FIXED-ANS", "12"});

            // DE 41 - Terminal ID (fixed 8 ANS)
            String tid = padRight("TERM0001", 8);
            fields.put(41, new String[]{"Card Acceptor Terminal ID",
                "TERM0001", tid, "FIXED-ANS", "8"});

            // DE 42 - Merchant ID (fixed 15 ANS)
            String mid = padRight("MERCHANT000001", 15);
            fields.put(42, new String[]{"Card Acceptor ID Code",
                "MERCHANT000001", mid, "FIXED-ANS", "15"});

            // DE 43 - Card Acceptor Name/Location (fixed 40 ANS)
            String desc = INPUT.get("description");
            if (desc.length() > 40) desc = desc.substring(0, 40);
            String desc40 = padRight(desc, 40);
            fields.put(43, new String[]{"Card Acceptor Name/Location (description)",
                desc, desc40, "FIXED-ANS", "40"});

            // DE 49 - Transaction Currency Code (fixed 3 AN)
            String txnCcy = CCY.getOrDefault(INPUT.get("debitCurrency"), "704");
            fields.put(49, new String[]{"Transaction Currency Code (debitCurrency)",
                INPUT.get("debitCurrency") + " → " + txnCcy, txnCcy, "FIXED-AN", "3"});

            // DE 51 - Billing Currency Code (fixed 3 AN)
            String bilCcy = CCY.getOrDefault(INPUT.get("creditCurrency"), "704");
            fields.put(51, new String[]{"Billing Currency Code (creditCurrency)",
                INPUT.get("creditCurrency") + " → " + bilCcy, bilCcy, "FIXED-AN", "3"});

            // DE 102 - Account ID 1 debitAccount (LLVAR, max 28)
            String acc1 = INPUT.get("debitAccount");
            fields.put(102, new String[]{"Account ID 1 (debitAccount)",
                acc1, llvar(acc1), "LLVAR-ANS", String.valueOf(acc1.length())});

            // DE 103 - Account ID 2 creditAccount (LLVAR, max 28)
            String acc2 = INPUT.get("creditAccount");
            fields.put(103, new String[]{"Account ID 2 (creditAccount)",
                acc2, llvar(acc2), "LLVAR-ANS", String.valueOf(acc2.length())});

            // ── Build bitmap ──────────────────────────────────────────────────
            long[] bitmap = buildBitmap(fields.keySet());
            String bitmapHex = String.format("%016X%016X", bitmap[0], bitmap[1]);
            String bitmapBin = toBinary(bitmap);

            // ── Assemble full message ─────────────────────────────────────────
            StringBuilder msgBody = new StringBuilder();
            for (Map.Entry<Integer, String[]> e : fields.entrySet()) {
                msgBody.append(e.getValue()[2]); // packed value
            }
            String fullMsg = "0200" + bitmapHex + msgBody;

            // ── Write log ─────────────────────────────────────────────────────
            header(w, now);

            section(w, "1. INPUT JSON (API Request)");
            INPUT.forEach((k, v) ->
                w.printf("   %-20s : %s%n", k, v.isEmpty() ? "(empty)" : v));

            section(w, "2. FIELD MAPPING (JSON → ISO 8583)");
            w.printf("   %-5s %-45s %-25s %-12s %s%n",
                "DE", "Field Name", "Input Value", "Type", "Packed Value");
            w.println("   " + "─".repeat(115));
            w.printf("   %-5s %-45s %-25s %-12s %s%n",
                "MTI", "Message Type Indicator", "-", "FIXED-N", "0200");
            for (Map.Entry<Integer, String[]> e : fields.entrySet()) {
                String[] f = e.getValue();
                w.printf("   DE%-3d %-45s %-25s %-12s %s%n",
                    e.getKey(),
                    truncate(f[0], 44),
                    truncate(f[1], 24),
                    f[3],
                    f[2]);
            }

            section(w, "3. BITMAP ANALYSIS");
            w.println("   Primary bitmap (DE 1-64)   : " + bitmapHex.substring(0, 16));
            w.println("   Secondary bitmap (DE 65-128): " + bitmapHex.substring(16, 32));
            w.println();
            w.println("   Bit layout (1=set, 0=not set):");
            w.println("   DE  1- 8  : " + bitmapBin.substring(0,  8));
            w.println("   DE  9-16  : " + bitmapBin.substring(8,  16));
            w.println("   DE 17-24  : " + bitmapBin.substring(16, 24));
            w.println("   DE 25-32  : " + bitmapBin.substring(24, 32));
            w.println("   DE 33-40  : " + bitmapBin.substring(32, 40));
            w.println("   DE 41-48  : " + bitmapBin.substring(40, 48));
            w.println("   DE 49-56  : " + bitmapBin.substring(48, 56));
            w.println("   DE 57-64  : " + bitmapBin.substring(56, 64));
            w.println("   DE 65-72  : " + bitmapBin.substring(64, 72));
            w.println("   DE 73-80  : " + bitmapBin.substring(72, 80));
            w.println("   DE 81-88  : " + bitmapBin.substring(80, 88));
            w.println("   DE 89-96  : " + bitmapBin.substring(88, 96));
            w.println("   DE 97-104 : " + bitmapBin.substring(96, 104));
            w.println("   DE105-112 : " + bitmapBin.substring(104, 112));
            w.println("   DE113-120 : " + bitmapBin.substring(112, 120));
            w.println("   DE121-128 : " + bitmapBin.substring(120, 128));
            w.println();
            w.print("   Active DEs : ");
            fields.keySet().forEach(de -> w.print("DE" + de + " "));
            w.println();

            section(w, "4. ASSEMBLED ISO 8583 MESSAGE (ASCII)");
            w.println("   Total length : " + fullMsg.length() + " chars");
            w.println();
            w.println("   [MTI    ] 0200");
            w.println("   [BITMAP ] " + bitmapHex);
            w.println("   [BODY   ] " + msgBody);
            w.println();
            w.println("   Full message (continuous):");
            // Print 80 chars per line
            for (int i = 0; i < fullMsg.length(); i += 80) {
                int end = Math.min(i + 80, fullMsg.length());
                w.printf("   [%04d-%04d] %s%n", i, end - 1, fullMsg.substring(i, end));
            }

            section(w, "5. WITH 2-BYTE LENGTH PREFIX (TCP/NACChannel)");
            byte[] msgBytes = fullMsg.getBytes("ASCII");
            int len = msgBytes.length;
            String lenHex = String.format("%02X%02X", (len >> 8) & 0xFF, len & 0xFF);
            w.println("   Message length : " + len + " bytes");
            w.println("   Length prefix  : 0x" + lenHex
                    + " (" + ((len >> 8) & 0xFF) + ", " + (len & 0xFF) + ")");
            w.println("   Full TCP frame : " + lenHex + bitmapHex + "...");

            section(w, "6. CONVERSION SUMMARY");
            w.println("   Status          : SUCCESS ✓");
            w.println("   Input fields    : " + INPUT.size());
            w.println("   ISO 8583 DEs set: " + fields.size() + " (+ MTI + BITMAP)");
            w.println("   Message length  : " + fullMsg.length() + " chars / " + len + " bytes");
            w.println("   MTI             : 0200 (Financial Transaction Request)");
            w.println("   STAN            : " + stan);
            w.println("   RRN             : " + rrn);
            w.println("   Timestamp       : " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            w.println("   Log file        : " + Paths.get(LOG_FILE).toAbsolutePath());

            footer(w);
        }

        System.out.println("================================================");
        System.out.println("  DONE! Log file created:");
        System.out.println("  " + Paths.get(LOG_FILE).toAbsolutePath());
        System.out.println("================================================");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String fmtAmt(String s) {
        try { return String.format("%012d", Long.parseLong(s.trim())); }
        catch (Exception e) { return "000000000000"; }
    }

    static String llvar(String s) {
        return String.format("%02d", s.length()) + s;
    }

    static String padRight(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static long[] buildBitmap(Set<Integer> des) {
        long primary = 0L, secondary = 0L;
        for (int de : des) {
            if (de >= 65) {
                secondary |= (1L << (128 - de));
            } else {
                primary |= (1L << (64 - de));
            }
        }
        // Bit 1 = secondary bitmap present
        if (secondary != 0) primary |= (1L << 63);
        return new long[]{primary, secondary};
    }

    static String toBinary(long[] bitmap) {
        return String.format("%64s", Long.toBinaryString(bitmap[0])).replace(' ', '0')
             + String.format("%64s", Long.toBinaryString(bitmap[1])).replace(' ', '0');
    }

    static void header(PrintWriter w, LocalDateTime now) {
        String ts = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        w.println("=".repeat(82));
        w.println("  ISO 8583 CONVERSION TEST LOG");
        w.println("  Generated at  : " + ts);
        w.println("  Description   : JSON transfer request → ISO 8583 MTI 0200");
        w.println("  Java version  : " + System.getProperty("java.version"));
        w.println("  OS            : " + System.getProperty("os.name"));
        w.println("=".repeat(82));
    }

    static void section(PrintWriter w, String title) {
        w.println();
        w.println("┌─ " + title + " " + "─".repeat(Math.max(2, 76 - title.length())));
    }

    static void footer(PrintWriter w) {
        w.println();
        w.println("=".repeat(82));
        w.println("  END OF LOG");
        w.println("=".repeat(82));
    }
}
