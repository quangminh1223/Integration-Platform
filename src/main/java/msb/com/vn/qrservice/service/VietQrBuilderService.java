package msb.com.vn.qrservice.service;

import msb.com.vn.qrservice.dto.request.GenerateVietQrRequest;
import msb.com.vn.qrservice.dto.response.VietQrData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class VietQrBuilderService {

    private static final String NAPAS_GUID = "A000000727";
    private static final String SERVICE_CODE_TRANSFER = "QRIBFTTA";
    private static final String SERVICE_CODE_PRESENT   = "QRIBFTTC";
    private static final String CURRENCY_VND = "704";
    private static final String COUNTRY_CODE = "VN";

    public String buildVietQrContent(GenerateVietQrRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));
        String method = (request.getAmount() != null) ? "12" : "11";
        sb.append(tlv("01", method));
        String accountInfo = buildAccountInfo(request.getBankBin(), request.getBankAccount());
        sb.append(tlv("38", accountInfo));
        sb.append(tlv("52", "0000"));
        sb.append(tlv("53", CURRENCY_VND));
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(tlv("54", request.getAmount().toBigInteger().toString()));
        }
        sb.append(tlv("58", COUNTRY_CODE));
        String accountName = (request.getAccountName() != null && !request.getAccountName().isBlank())
                ? request.getAccountName() : "NGUOI DUNG";
        sb.append(tlv("59", accountName));
        sb.append(tlv("60", "VIET NAM"));
        String additionalData = buildAdditionalData(request.getDescription(), request.getTransactionRef());
        if (!additionalData.isEmpty()) {
            sb.append(tlv("62", additionalData));
        }
        sb.append("6304");
        sb.append(calculateCrc16(sb.toString()));
        log.debug("Đã xây dựng VietQR content: length={}", sb.length());
        return sb.toString();
    }

    public VietQrData parseVietQrContent(String content) {
        VietQrData.VietQrDataBuilder builder = VietQrData.builder();
        builder.countryCode(COUNTRY_CODE);
        builder.currency("VND");
        int index = 0;
        while (index < content.length() - 4) {
            if (index + 4 > content.length()) break;
            String tag = content.substring(index, index + 2);
            int length;
            try {
                length = Integer.parseInt(content.substring(index + 2, index + 4));
            } catch (NumberFormatException e) { break; }
            if (index + 4 + length > content.length()) break;
            String value = content.substring(index + 4, index + 4 + length);
            switch (tag) {
                case "38" -> parseAccountInfo(value, builder);
                case "53" -> builder.currency(value.equals(CURRENCY_VND) ? "VND" : value);
                case "54" -> { try { builder.amount(new BigDecimal(value)); } catch (NumberFormatException ignored) {} }
                case "58" -> builder.countryCode(value);
                case "59" -> builder.accountName(value);
                case "62" -> parseAdditionalData(value, builder);
                default -> log.trace("Tag không xử lý: {}={}", tag, value);
            }
            index += 4 + length;
        }
        return builder.build();
    }

    private String buildAccountInfo(String bankBin, String bankAccount) {
        String guid = tlv("00", NAPAS_GUID);
        String binField = tlv("00", bankBin);
        String accountField = tlv("01", bankAccount);
        String serviceField = tlv("02", SERVICE_CODE_TRANSFER);
        String accountInfo = tlv("01", binField + accountField + serviceField);
        return guid + accountInfo;
    }

    private void parseAccountInfo(String value, VietQrData.VietQrDataBuilder builder) {
        int idx = 0;
        while (idx < value.length()) {
            if (idx + 4 > value.length()) break;
            String tag = value.substring(idx, idx + 2);
            int len;
            try { len = Integer.parseInt(value.substring(idx + 2, idx + 4)); } catch (NumberFormatException e) { break; }
            if (idx + 4 + len > value.length()) break;
            String val = value.substring(idx + 4, idx + 4 + len);
            if ("01".equals(tag)) parseNestedAccountInfo(val, builder);
            idx += 4 + len;
        }
    }

    private void parseNestedAccountInfo(String value, VietQrData.VietQrDataBuilder builder) {
        int idx = 0;
        while (idx < value.length()) {
            if (idx + 4 > value.length()) break;
            String tag = value.substring(idx, idx + 2);
            int len;
            try { len = Integer.parseInt(value.substring(idx + 2, idx + 4)); } catch (NumberFormatException e) { break; }
            if (idx + 4 + len > value.length()) break;
            String val = value.substring(idx + 4, idx + 4 + len);
            switch (tag) {
                case "00" -> builder.bankBin(val);
                case "01" -> builder.bankAccount(val);
                case "02" -> builder.serviceCode(val);
                default -> {}
            }
            idx += 4 + len;
        }
    }

    private String buildAdditionalData(String description, String transactionRef) {
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

    private void parseAdditionalData(String value, VietQrData.VietQrDataBuilder builder) {
        int idx = 0;
        while (idx < value.length()) {
            if (idx + 4 > value.length()) break;
            String tag = value.substring(idx, idx + 2);
            int len;
            try { len = Integer.parseInt(value.substring(idx + 2, idx + 4)); } catch (NumberFormatException e) { break; }
            if (idx + 4 + len > value.length()) break;
            String val = value.substring(idx + 4, idx + 4 + len);
            switch (tag) {
                case "05" -> builder.transactionRef(val);
                case "08" -> builder.description(val);
                default -> {}
            }
            idx += 4 + len;
        }
    }

    private String tlv(String tag, String value) {
        return tag + String.format("%02d", value.length()) + value;
    }

    public String calculateCrc16(String data) {
        int crc = 0xFFFF;
        byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte b : bytes) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & 0x8000) != 0) ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    public boolean verifyCrc(String content) {
        if (content.length() < 4) return false;
        String dataWithoutCrc = content.substring(0, content.length() - 4);
        String expectedCrc = content.substring(content.length() - 4);
        return calculateCrc16(dataWithoutCrc + "6304").equalsIgnoreCase(expectedCrc);
    }
}
