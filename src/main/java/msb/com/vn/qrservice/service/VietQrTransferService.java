package msb.com.vn.qrservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.dto.response.VietQrData;
import msb.com.vn.qrservice.swagger.service.ResilientBackendCaller;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Luồng xử lý: Mã VietQR → Parse → Mapping → Gọi backend createMsbAccountTransfer.
 *
 * Flow:
 * ┌──────────────┐     ┌────────────────────┐     ┌─────────────────────────┐     ┌──────────────┐
 * │ Nhận mã QR   │────▶│ VietQrBuilderService│────▶│ Mapping → Swagger body  │────▶│ Backend T24  │
 * │ (chuỗi TLV)  │     │ parse → VietQrData  │     │ createMsbAccountTransfer│     │ /accttrf/create│
 * └──────────────┘     └────────────────────┘     └─────────────────────────┘     └──────────────┘
 *
 * Mapping VietQR → MsbAccountTransferBody:
 * ─────────────────────────────────────────────────────────────────────────────
 * VietQR field        →  Swagger field                    Ghi chú
 * ─────────────────────────────────────────────────────────────────────────────
 * bankAccount         →  body.creditAccount               TK thụ hưởng
 * amount              →  body.creditAmount, debitAmount    Số tiền
 * currency (VND)      →  body.creditCurrency, debitCurrency
 * description         →  body.paymentDetails[0].paymentDetail
 * transactionRef      →  body.msbTransSeq                 Mã tham chiếu
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Các field bổ sung (truyền thêm từ caller):
 * - debitAccount: TK ghi nợ (bên gửi)
 * - tranCode: mã giao dịch (msbTransCode)
 * - channel: kênh giao dịch (msbChannel)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VietQrTransferService {

    private final VietQrBuilderService vietQrBuilderService;
    private final ResilientBackendCaller backendCaller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String OPERATION_ID = "createMsbAccountTransfer";

    /**
     * Nhận mã VietQR + thông tin bổ sung → parse → gọi backend chuyển khoản.
     *
     * @param qrContent      chuỗi VietQR (TLV format, bao gồm CRC)
     * @param debitAccount   tài khoản ghi nợ (bên gửi tiền)
     * @param tranCode       mã giao dịch (ví dụ: "PAYOO001", "IBFT001")
     * @param channel        kênh giao dịch (ví dụ: "PMG01", "MOBILE")
     * @param headers        headers bổ sung (credentials, companyId...)
     * @return kết quả giao dịch
     */
    public Map<String, Object> processQrTransfer(String qrContent,
                                                   String debitAccount,
                                                   String tranCode,
                                                   String channel,
                                                   Map<String, String> headers) {

        log.info("Bắt đầu xử lý VietQR transfer: qrLength={}, debitAccount={}",
                qrContent.length(), debitAccount);

        // 1. Verify CRC
        if (!vietQrBuilderService.verifyCrc(qrContent)) {
            log.warn("VietQR CRC không hợp lệ");
            return errorResponse("QR_INVALID_CRC", "Mã QR không hợp lệ (CRC sai)");
        }

        // 2. Parse VietQR → VietQrData
        VietQrData qrData = vietQrBuilderService.parseVietQrContent(qrContent);
        log.info("Parse VietQR thành công: creditAccount={}, amount={}, bankBin={}",
                qrData.getBankAccount(), qrData.getAmount(), qrData.getBankBin());

        // 3. Validate dữ liệu parse được
        if (qrData.getBankAccount() == null || qrData.getBankAccount().isBlank()) {
            return errorResponse("QR_NO_ACCOUNT", "Mã QR không chứa số tài khoản thụ hưởng");
        }

        // 4. Build payload swagger
        ObjectNode payload = buildTransferPayload(qrData, debitAccount, tranCode, channel);

        // 5. Gọi backend
        RestResponse<JsonNode> response = backendCaller.call(
                OPERATION_ID,
                Map.of(),
                payload,
                headers
        );

        // 6. Build response
        return buildTransferResponse(response, qrData, debitAccount);
    }

    /**
     * Overload: nhận thêm debitAmount riêng (nếu khác amount trong QR).
     */
    public Map<String, Object> processQrTransfer(String qrContent,
                                                   String debitAccount,
                                                   String debitAmount,
                                                   String tranCode,
                                                   String channel,
                                                   Map<String, String> headers) {

        log.info("Bắt đầu xử lý VietQR transfer (custom amount): debitAmount={}", debitAmount);

        if (!vietQrBuilderService.verifyCrc(qrContent)) {
            return errorResponse("QR_INVALID_CRC", "Mã QR không hợp lệ (CRC sai)");
        }

        VietQrData qrData = vietQrBuilderService.parseVietQrContent(qrContent);

        if (qrData.getBankAccount() == null || qrData.getBankAccount().isBlank()) {
            return errorResponse("QR_NO_ACCOUNT", "Mã QR không chứa số tài khoản thụ hưởng");
        }

        ObjectNode payload = buildTransferPayload(qrData, debitAccount, tranCode, channel);

        // Override amount nếu truyền riêng
        if (debitAmount != null && !debitAmount.isBlank()) {
            ObjectNode body = (ObjectNode) payload.get("body");
            body.put("debitAmount", debitAmount);
            body.put("creditAmount", debitAmount);
        }

        RestResponse<JsonNode> response = backendCaller.call(
                OPERATION_ID, Map.of(), payload, headers
        );

        return buildTransferResponse(response, qrData, debitAccount);
    }

    // ─── Build payload theo swagger schema ────────────────────────────────────

    private ObjectNode buildTransferPayload(VietQrData qrData,
                                             String debitAccount,
                                             String tranCode,
                                             String channel) {
        ObjectNode root = objectMapper.createObjectNode();

        // Header
        ObjectNode header = objectMapper.createObjectNode();
        header.putObject("override");
        header.putObject("audit");
        root.set("header", header);

        // Body (MsbAccountTransferBody)
        ObjectNode body = objectMapper.createObjectNode();

        // Required fields
        body.put("debitCurrency", resolveCurrency(qrData.getCurrency()));
        body.put("creditCurrency", resolveCurrency(qrData.getCurrency()));
        body.put("msbTransCode", tranCode != null ? tranCode : "QRTRF001");
        body.put("msbTransSeq", qrData.getTransactionRef() != null
                ? qrData.getTransactionRef() : generateTransSeq());

        // Accounts
        if (debitAccount != null && !debitAccount.isBlank()) {
            body.put("debitAccount", debitAccount);
        }
        body.put("creditAccount", qrData.getBankAccount());

        // Amount
        if (qrData.getAmount() != null) {
            String amountStr = qrData.getAmount().toBigInteger().toString();
            body.put("debitAmount", amountStr);
            body.put("creditAmount", amountStr);
        }

        // Description → paymentDetails
        if (qrData.getDescription() != null && !qrData.getDescription().isBlank()) {
            var detail = objectMapper.createObjectNode();
            detail.put("paymentDetail", truncate(qrData.getDescription(), 50));
            var details = objectMapper.createArrayNode();
            details.add(detail);
            body.set("paymentDetails", details);
        }

        // Channel
        if (channel != null && !channel.isBlank()) {
            body.put("msbChannel", channel);
        }

        root.set("body", body);
        return root;
    }

    // ─── Build response ───────────────────────────────────────────────────────

    private Map<String, Object> buildTransferResponse(RestResponse<JsonNode> response,
                                                       VietQrData qrData,
                                                       String debitAccount) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (response.isSuccess() && response.getBody() != null) {
            JsonNode respBody = response.getBody();

            result.put("success", true);
            result.put("transactionId", extractField(respBody, "/header/id"));
            result.put("status", extractField(respBody, "/header/status"));
            result.put("debitAccount", debitAccount);
            result.put("creditAccount", qrData.getBankAccount());
            result.put("amount", qrData.getAmount() != null ? qrData.getAmount().toString() : "");
            result.put("currency", resolveCurrency(qrData.getCurrency()));
            result.put("bankBin", qrData.getBankBin());
            result.put("description", qrData.getDescription());
        } else {
            result.put("success", false);
            result.put("transactionId", "");
            result.put("status", "FAILED");
            result.put("error", response.getErrorMessage());
            result.put("creditAccount", qrData.getBankAccount());
        }

        return result;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> errorResponse(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("errorCode", code);
        result.put("error", message);
        return result;
    }

    private String resolveCurrency(String currency) {
        if (currency == null || currency.isBlank()) return "VND";
        // VietQR dùng ISO 4217 numeric "704" → convert sang alpha
        return switch (currency) {
            case "704", "VND" -> "VND";
            case "840", "USD" -> "USD";
            case "978", "EUR" -> "EUR";
            default -> currency;
        };
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String generateTransSeq() {
        return String.valueOf(System.currentTimeMillis() % 1000000000);
    }

    private String extractField(JsonNode root, String jsonPointer) {
        if (root == null) return "";
        JsonNode node = root.at(jsonPointer);
        return (node != null && !node.isMissingNode() && !node.isNull()) ? node.asText() : "";
    }
}
