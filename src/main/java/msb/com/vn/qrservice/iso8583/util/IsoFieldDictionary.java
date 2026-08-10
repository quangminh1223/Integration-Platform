package msb.com.vn.qrservice.iso8583.util;

import java.util.Map;

/**
 * Từ điển field ISO8583: số field → tên đầy đủ và khóa camelCase.
 *
 * <p>Dùng cho việc convert ISO sang JSON và ghi log để người đọc hiểu được
 * ý nghĩa field mà không phải tra bảng.</p>
 */
public final class IsoFieldDictionary {

    /** Khóa camelCase cho các field dùng thường xuyên — đưa vào JSON để dễ tiêu thụ */
    private static final Map<Integer, String> JSON_KEYS = Map.ofEntries(
            Map.entry(2, "pan"),
            Map.entry(3, "processingCode"),
            Map.entry(4, "amount"),
            Map.entry(5, "settlementAmount"),
            Map.entry(6, "billingAmount"),
            Map.entry(7, "transmissionDateTime"),
            Map.entry(9, "settlementConversionRate"),
            Map.entry(11, "stan"),
            Map.entry(12, "localTransactionTime"),
            Map.entry(13, "localTransactionDate"),
            Map.entry(14, "expirationDate"),
            Map.entry(15, "settlementDate"),
            Map.entry(18, "merchantType"),
            Map.entry(19, "acquiringCountryCode"),
            Map.entry(22, "posEntryMode"),
            Map.entry(25, "posConditionCode"),
            Map.entry(28, "transactionFeeAmount"),
            Map.entry(32, "acquiringInstitutionId"),
            Map.entry(33, "forwardingInstitutionId"),
            Map.entry(35, "track2Data"),
            Map.entry(36, "track3Data"),
            Map.entry(37, "rrn"),
            Map.entry(38, "authorizationIdResponse"),
            Map.entry(39, "responseCode"),
            Map.entry(41, "terminalId"),
            Map.entry(42, "merchantId"),
            Map.entry(43, "merchantNameLocation"),
            Map.entry(44, "additionalResponseData"),
            Map.entry(45, "track1Data"),
            Map.entry(48, "additionalDataPrivate"),
            Map.entry(49, "transactionCurrencyCode"),
            Map.entry(50, "settlementCurrencyCode"),
            Map.entry(52, "pinData"),
            Map.entry(53, "securityControlInfo"),
            Map.entry(54, "additionalAmounts"),
            Map.entry(55, "iccData"),
            Map.entry(64, "mac"),
            Map.entry(70, "networkManagementCode"),
            Map.entry(90, "originalDataElements"),
            Map.entry(102, "accountIdentification1"),
            Map.entry(103, "accountIdentification2"),
            Map.entry(104, "transactionDescription"),
            Map.entry(128, "macExtended")
    );

    private IsoFieldDictionary() {
    }

    /**
     * Khóa camelCase để đưa vào JSON. Field không có tên riêng trả về {@code field{N}}.
     */
    public static String jsonKey(int fieldNumber) {
        return JSON_KEYS.getOrDefault(fieldNumber, "field" + fieldNumber);
    }

    /**
     * Tên đầy đủ theo chuẩn ISO8583.
     */
    public static String fieldName(int fieldNumber) {
        return switch (fieldNumber) {
            case 1 -> "Secondary Bitmap";
            case 2 -> "Primary Account Number (PAN)";
            case 3 -> "Processing Code";
            case 4 -> "Transaction Amount";
            case 5 -> "Settlement Amount";
            case 6 -> "Cardholder Billing Amount";
            case 7 -> "Transmission Date and Time";
            case 8 -> "Cardholder Billing Fee Amount";
            case 9 -> "Settlement Conversion Rate";
            case 10 -> "Cardholder Billing Conversion Rate";
            case 11 -> "System Trace Audit Number (STAN)";
            case 12 -> "Local Transaction Time";
            case 13 -> "Local Transaction Date";
            case 14 -> "Expiration Date";
            case 15 -> "Settlement Date";
            case 16 -> "Currency Conversion Date";
            case 17 -> "Capture Date";
            case 18 -> "Merchant Type (MCC)";
            case 19 -> "Acquiring Institution Country Code";
            case 20 -> "PAN Extended Country Code";
            case 21 -> "Forwarding Institution Country Code";
            case 22 -> "Point of Service Entry Mode";
            case 23 -> "Application PAN Sequence Number";
            case 24 -> "Network International Identifier (NII)";
            case 25 -> "Point of Service Condition Code";
            case 26 -> "Point of Service PIN Capture Code";
            case 27 -> "Authorizing Identification Response Length";
            case 28 -> "Transaction Fee Amount";
            case 29 -> "Settlement Fee Amount";
            case 30 -> "Transaction Processing Fee Amount";
            case 31 -> "Settlement Processing Fee Amount";
            case 32 -> "Acquiring Institution Identification Code";
            case 33 -> "Forwarding Institution Identification Code";
            case 34 -> "Primary Account Number Extended";
            case 35 -> "Track 2 Data";
            case 36 -> "Track 3 Data";
            case 37 -> "Retrieval Reference Number (RRN)";
            case 38 -> "Authorization Identification Response";
            case 39 -> "Response Code";
            case 40 -> "Service Restriction Code";
            case 41 -> "Card Acceptor Terminal Identification";
            case 42 -> "Card Acceptor Identification Code";
            case 43 -> "Card Acceptor Name/Location";
            case 44 -> "Additional Response Data";
            case 45 -> "Track 1 Data";
            case 46 -> "Additional Data ISO";
            case 47 -> "Additional Data National";
            case 48 -> "Additional Data Private";
            case 49 -> "Transaction Currency Code";
            case 50 -> "Settlement Currency Code";
            case 51 -> "Cardholder Billing Currency Code";
            case 52 -> "Personal Identification Number (PIN) Data";
            case 53 -> "Security Related Control Information";
            case 54 -> "Additional Amounts";
            case 55 -> "ICC Data (EMV)";
            case 60 -> "Reserved Private 60";
            case 61 -> "Reserved Private 61";
            case 62 -> "Reserved Private 62";
            case 63 -> "Reserved Private 63";
            case 64 -> "Message Authentication Code (MAC)";
            case 70 -> "Network Management Information Code";
            case 90 -> "Original Data Elements";
            case 95 -> "Replacement Amounts";
            case 100 -> "Receiving Institution Identification Code";
            case 102 -> "Account Identification 1";
            case 103 -> "Account Identification 2";
            case 104 -> "Transaction Description";
            case 128 -> "Message Authentication Code (MAC) Extended";
            default -> "Field " + fieldNumber;
        };
    }

    /**
     * Mô tả MTI theo chuẩn ISO8583.
     */
    public static String mtiDescription(String mti) {
        if (mti == null || mti.length() < 4) {
            return "Unknown";
        }
        return switch (mti) {
            case "0100" -> "Authorization Request";
            case "0110" -> "Authorization Response";
            case "0120" -> "Authorization Advice";
            case "0130" -> "Authorization Advice Response";
            case "0200" -> "Financial Transaction Request";
            case "0210" -> "Financial Transaction Response";
            case "0220" -> "Financial Transaction Advice";
            case "0230" -> "Financial Transaction Advice Response";
            case "0400" -> "Reversal Request";
            case "0410" -> "Reversal Response";
            case "0420" -> "Reversal Advice";
            case "0430" -> "Reversal Advice Response";
            case "0800" -> "Network Management Request";
            case "0810" -> "Network Management Response";
            case "0820" -> "Network Management Advice";
            default -> "Unknown MTI: " + mti;
        };
    }
}
