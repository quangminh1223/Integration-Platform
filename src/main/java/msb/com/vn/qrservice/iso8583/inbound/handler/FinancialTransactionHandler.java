package msb.com.vn.qrservice.iso8583.inbound.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.config.Iso8583PackagerProvider;
import msb.com.vn.qrservice.iso8583.inbound.Iso8583MessageHandler;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.transform.Iso8583JsonConverter;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Xử lý bản tin giao dịch tài chính — MTI 0100 (authorization) và 0200 (financial).
 *
 * <p>Đọc dữ liệu từ bản JSON đã convert ({@code exchange.getJsonRequest()}) thay vì
 * đọc trực tiếp số field ISO, để nghiệp vụ không phụ thuộc layout ISO8583.</p>
 *
 * <p>Đây là điểm cắm business logic: hiện tại validate cơ bản và trả approved.
 * Khi tích hợp core banking, thay phần TODO bằng lệnh gọi service tương ứng.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialTransactionHandler implements Iso8583MessageHandler {

    private static final Set<String> SUPPORTED_MTI = Set.of("0100", "0200");

    /** 00 = Approved */
    private static final String RC_APPROVED = "00";
    /** 30 = Format error */
    private static final String RC_FORMAT_ERROR = "30";
    /** 13 = Invalid amount */
    private static final String RC_INVALID_AMOUNT = "13";

    /** Khóa JSON bắt buộc phải có (tương ứng field 3, 4, 7, 11) */
    private static final String[] MANDATORY_KEYS =
            {"processingCode", "amount", "transmissionDateTime", "stan"};

    /** Field echo lại trong response */
    private static final int[] ECHO_FIELDS = {2, 3, 4, 7, 11, 12, 13, 32, 37, 41, 42, 49};

    private final Iso8583PackagerProvider packagerProvider;

    @Override
    public String getName() {
        return "financial-transaction-handler";
    }

    @Override
    public boolean supports(String mti) {
        return SUPPORTED_MTI.contains(mti);
    }

    @Override
    public ISOMsg handle(Iso8583Exchange exchange) throws Exception {
        ISOMsg request = exchange.getIsoRequest();
        String correlationId = exchange.getCorrelationId();

        log.info("Giao dịch tài chính đến: {} correlationId={}",
                IsoMessageUtils.summarize(request), correlationId);

        ISOMsg response = packagerProvider.newMessage();
        response.setMTI(IsoMessageUtils.deriveResponseMti(exchange.getMti()));

        for (int field : ECHO_FIELDS) {
            if (request.hasField(field)) {
                response.set(field, request.getString(field));
            }
        }
        IsoMessageUtils.fillTimeFields(response);

        String responseCode = validate(exchange);
        response.set(IsoMessageUtils.FIELD_RESPONSE_CODE, responseCode);

        if (RC_APPROVED.equals(responseCode)) {
            // TODO: gọi core banking / TransferMappingService tại đây khi tích hợp thật.
            //       Dữ liệu đầu vào lấy từ exchange.getJsonRequest().get("data")
            if (!response.hasField(IsoMessageUtils.FIELD_RRN)) {
                response.set(IsoMessageUtils.FIELD_RRN, generateRrn(correlationId));
            }
            // Field 38 — Authorization ID Response
            response.set(38, generateAuthId(correlationId));
        }

        log.info("Trả response: {} correlationId={}",
                IsoMessageUtils.summarize(response), correlationId);
        return response;
    }

    /**
     * Validate dựa trên bản JSON đã convert, trả về response code tương ứng.
     */
    private String validate(Iso8583Exchange exchange) {
        Map<String, String> data = extractData(exchange);
        String correlationId = exchange.getCorrelationId();

        for (String key : MANDATORY_KEYS) {
            String value = data.get(key);
            if (value == null || value.isBlank()) {
                log.warn("Thiếu dữ liệu bắt buộc '{}' correlationId={}", key, correlationId);
                return RC_FORMAT_ERROR;
            }
        }

        String amountRaw = data.get("amount");
        try {
            long amount = Long.parseLong(amountRaw.trim());
            if (amount <= 0) {
                log.warn("Số tiền không hợp lệ: {} correlationId={}", amount, correlationId);
                return RC_INVALID_AMOUNT;
            }
        } catch (NumberFormatException e) {
            log.warn("Số tiền không phải số: '{}' correlationId={}", amountRaw, correlationId);
            return RC_INVALID_AMOUNT;
        }

        return RC_APPROVED;
    }

    /**
     * Lấy map dữ liệu nghiệp vụ từ JSON đã convert.
     * Nếu conversion bị tắt, fallback đọc trực tiếp từ ISOMsg.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractData(Iso8583Exchange exchange) {
        Map<String, Object> json = exchange.getJsonRequest();
        if (json != null) {
            Object data = json.get(Iso8583JsonConverter.KEY_DATA);
            if (data instanceof Map<?, ?> map) {
                return (Map<String, String>) map;
            }
        }

        log.debug("Không có jsonRequest, đọc trực tiếp từ ISOMsg correlationId={}",
                exchange.getCorrelationId());
        ISOMsg msg = exchange.getIsoRequest();
        return Map.of(
                "processingCode", nullToEmpty(msg, 3),
                "amount", nullToEmpty(msg, 4),
                "transmissionDateTime", nullToEmpty(msg, 7),
                "stan", nullToEmpty(msg, 11)
        );
    }

    private String nullToEmpty(ISOMsg msg, int field) {
        return msg.hasField(field) && msg.getString(field) != null ? msg.getString(field) : "";
    }

    private String generateRrn(String correlationId) {
        String digits = correlationId.replaceAll("\\D", "");
        String base = digits.length() >= 12 ? digits.substring(digits.length() - 12) : digits;
        return String.format("%12s", base).replace(' ', '0');
    }

    private String generateAuthId(String correlationId) {
        int hash = Math.abs(correlationId.hashCode() % 1_000_000);
        return String.format("%06d", hash);
    }
}
