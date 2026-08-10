package msb.com.vn.qrservice.service;

import msb.com.vn.qrservice.common.exception.Iso8583Exception;
import msb.com.vn.qrservice.dto.request.Iso8583BuildRequest;
import msb.com.vn.qrservice.dto.request.Iso8583ParseRequest;
import msb.com.vn.qrservice.dto.response.Iso8583BuildResponse;
import msb.com.vn.qrservice.dto.response.Iso8583ParseResponse;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service xử lý bản tin ISO8583 sử dụng jPOS
 */
@Slf4j
@Service
public class Iso8583Service {

    @Value("${iso8583.packager.config:iso8583/packager.xml}")
    private String packagerConfig;

    private GenericPackager packager;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(packagerConfig);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    packager = new GenericPackager(is);
                    log.info("Khởi tạo ISO8583 packager từ config: {}", packagerConfig);
                }
            } else {
                // Dùng packager mặc định ISO87APackager nếu không có file config
                packager = new GenericPackager(
                        getClass().getResourceAsStream("/org/jpos/iso/packager/iso87ascii.xml")
                );
                log.warn("Không tìm thấy config {}, dùng packager mặc định iso87ascii", packagerConfig);
            }
        } catch (Exception e) {
            log.error("Lỗi khởi tạo ISO8583 packager: {}", e.getMessage(), e);
            throw new IllegalStateException("Không thể khởi tạo ISO8583 packager", e);
        }
    }

    /**
     * Parse bản tin ISO8583 từ hex string sang các field
     */
    public Iso8583ParseResponse parseMessage(Iso8583ParseRequest request) {
        String hexMessage = request.getHexMessage().trim().toUpperCase();
        log.debug("Parse ISO8583 message: length={}", hexMessage.length());

        byte[] rawBytes = hexToBytes(hexMessage);

        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);

        try {
            isoMsg.unpack(rawBytes);
        } catch (ISOException e) {
            log.error("Lỗi parse ISO8583: {}", e.getMessage());
            throw new Iso8583Exception("Bản tin ISO8583 không hợp lệ: " + e.getMessage(), e);
        }

        return buildParseResponse(isoMsg);
    }

    /**
     * Build bản tin ISO8583 từ các field sang hex string
     */
    public Iso8583BuildResponse buildMessage(Iso8583BuildRequest request) {
        log.debug("Build ISO8583 message: mti={}, fields={}", request.getMti(), request.getFields().size());

        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);

        try {
            isoMsg.setMTI(request.getMti());

            for (Map.Entry<Integer, String> entry : request.getFields().entrySet()) {
                int fieldNum = entry.getKey();
                String value = entry.getValue();
                if (fieldNum < 1 || fieldNum > 128) {
                    throw new Iso8583Exception("Số field không hợp lệ: " + fieldNum + " (phải từ 1-128)");
                }
                isoMsg.set(fieldNum, value);
            }

            byte[] packed = isoMsg.pack();
            String hexResult = bytesToHex(packed);

            return Iso8583BuildResponse.builder()
                    .hexMessage(hexResult)
                    .mti(request.getMti())
                    .messageLength(packed.length)
                    .processedAt(LocalDateTime.now())
                    .build();

        } catch (Iso8583Exception e) {
            throw e;
        } catch (ISOException e) {
            log.error("Lỗi build ISO8583: {}", e.getMessage());
            throw new Iso8583Exception("Không thể build bản tin ISO8583: " + e.getMessage(), e);
        }
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private Iso8583ParseResponse buildParseResponse(ISOMsg isoMsg) {
        Map<Integer, Iso8583ParseResponse.FieldInfo> fieldMap = new LinkedHashMap<>();

        try {
            // Duyệt field 1-128
            for (int i = 1; i <= 128; i++) {
                if (isoMsg.hasField(i)) {
                    String value = isoMsg.getString(i);
                    if (value != null) {
                        fieldMap.put(i, Iso8583ParseResponse.FieldInfo.builder()
                                .fieldNumber(i)
                                .fieldName(getFieldName(i))
                                .value(value)
                                .length(value.length())
                                .build());
                    }
                }
            }

            String mti = isoMsg.getMTI();
            String primaryBitmap   = extractBitmap(isoMsg, false);
            String secondaryBitmap = isoMsg.hasField(1) ? extractBitmap(isoMsg, true) : null;

            return Iso8583ParseResponse.builder()
                    .mti(mti)
                    .mtiDescription(getMtiDescription(mti))
                    .primaryBitmap(primaryBitmap)
                    .secondaryBitmap(secondaryBitmap)
                    .fields(fieldMap)
                    .totalFields(fieldMap.size())
                    .processedAt(LocalDateTime.now())
                    .build();

        } catch (ISOException e) {
            throw new Iso8583Exception("Lỗi đọc dữ liệu ISO8583: " + e.getMessage(), e);
        }
    }

    private String extractBitmap(ISOMsg isoMsg, boolean secondary) {
        try {
            byte[] bitmap = isoMsg.getBytes(secondary ? 1 : 0);
            return bitmap != null ? bytesToHex(bitmap) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new Iso8583Exception("Hex string không được để trống");
        }
        // Loại bỏ khoảng trắng nếu có
        hex = hex.replaceAll("\\s+", "");
        if (hex.length() % 2 != 0) {
            throw new Iso8583Exception("Hex string phải có độ dài chẵn, nhận được: " + hex.length());
        }
        try {
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        } catch (NumberFormatException e) {
            throw new Iso8583Exception("Hex string chứa ký tự không hợp lệ: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Mô tả MTI theo chuẩn ISO8583
     */
    private String getMtiDescription(String mti) {
        if (mti == null || mti.length() < 4) return "Unknown";
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
            default    -> "Unknown MTI: " + mti;
        };
    }

    /**
     * Tên các field ISO8583 phổ biến
     */
    private String getFieldName(int fieldNumber) {
        return switch (fieldNumber) {
            case 1  -> "Secondary Bitmap";
            case 2  -> "Primary Account Number (PAN)";
            case 3  -> "Processing Code";
            case 4  -> "Transaction Amount";
            case 5  -> "Settlement Amount";
            case 6  -> "Cardholder Billing Amount";
            case 7  -> "Transmission Date and Time";
            case 8  -> "Cardholder Billing Fee Amount";
            case 9  -> "Settlement Conversion Rate";
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
            case 56 -> "Reserved ISO";
            case 57 -> "Reserved National";
            case 58 -> "Reserved National";
            case 59 -> "Reserved National";
            case 60 -> "Reserved Private";
            case 61 -> "Reserved Private";
            case 62 -> "Reserved Private";
            case 63 -> "Reserved Private";
            case 64 -> "Message Authentication Code (MAC)";
            case 65 -> "Extended Bitmap Indicator";
            case 66 -> "Settlement Code";
            case 67 -> "Extended Payment Code";
            case 68 -> "Receiving Institution Country Code";
            case 69 -> "Settlement Institution Country Code";
            case 70 -> "Network Management Information Code";
            case 71 -> "Message Number";
            case 72 -> "Message Number Last";
            case 73 -> "Action Date";
            case 74 -> "Credits Number";
            case 75 -> "Credits Reversal Number";
            case 76 -> "Debits Number";
            case 77 -> "Debits Reversal Number";
            case 78 -> "Transfer Number";
            case 79 -> "Transfer Reversal Number";
            case 80 -> "Inquiries Number";
            case 81 -> "Authorizations Number";
            case 82 -> "Credits Processing Fee Amount";
            case 83 -> "Credits Transaction Fee Amount";
            case 84 -> "Debits Processing Fee Amount";
            case 85 -> "Debits Transaction Fee Amount";
            case 86 -> "Credits Amount";
            case 87 -> "Credits Reversal Amount";
            case 88 -> "Debits Amount";
            case 89 -> "Debits Reversal Amount";
            case 90 -> "Original Data Elements";
            case 91 -> "File Update Code";
            case 92 -> "File Security Code";
            case 93 -> "Response Indicator";
            case 94 -> "Service Indicator";
            case 95 -> "Replacement Amounts";
            case 96 -> "Message Security Code";
            case 97 -> "Net Settlement Amount";
            case 98 -> "Payee";
            case 99 -> "Settlement Institution Identification Code";
            case 100 -> "Receiving Institution Identification Code";
            case 101 -> "File Name";
            case 102 -> "Account Identification 1";
            case 103 -> "Account Identification 2";
            case 104 -> "Transaction Description";
            case 105 -> "Reserved ISO";
            case 106 -> "Reserved ISO";
            case 107 -> "Reserved ISO";
            case 108 -> "Reserved ISO";
            case 109 -> "Reserved ISO";
            case 110 -> "Reserved ISO";
            case 111 -> "Reserved ISO";
            case 112 -> "Reserved National";
            case 113 -> "Reserved National";
            case 114 -> "Reserved National";
            case 115 -> "Reserved National";
            case 116 -> "Reserved National";
            case 117 -> "Reserved National";
            case 118 -> "Reserved National";
            case 119 -> "Reserved National";
            case 120 -> "Reserved Private";
            case 121 -> "Reserved Private";
            case 122 -> "Reserved Private";
            case 123 -> "Reserved Private";
            case 124 -> "Reserved Private";
            case 125 -> "Reserved Private";
            case 126 -> "Reserved Private";
            case 127 -> "Reserved Private";
            case 128 -> "Message Authentication Code (MAC) Extended";
            default  -> "Field " + fieldNumber;
        };
    }
}
