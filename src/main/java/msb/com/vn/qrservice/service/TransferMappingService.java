package msb.com.vn.qrservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.http.RestResponse;
import msb.com.vn.qrservice.swagger.service.ResilientBackendCaller;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapping field cho API createMsbAccountTransfer.
 *
 * Đây là nơi viết logic mapping giữa:
 * - Input JSON (từ hệ thống nội bộ gửi vào)
 * - Output JSON (theo schema swagger MsbAccountTransferBody)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * MAPPING TABLE:
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Input (nội bộ)         →  Swagger (MsbAccountTransferBody)    Required?
 * ─────────────────────────────────────────────────────────────────────────────
 * debitAccount            →  body.debitAccount                   no (max 36)
 * debitCurrency           →  body.debitCurrency                  YES (max 3)
 * debitAmount             →  body.debitAmount                    no (max 18)
 * creditAccount           →  body.creditAccount                  no (max 36)
 * creditCurrency          →  body.creditCurrency                 YES (max 3)
 * creditAmount            →  body.creditAmount                   no (max 18)
 * creditRate / debitRate  →  body.customerRate                   no (max 16)
 * description             →  body.paymentDetails[0].paymentDetail  no (max 50)
 * serviceFee              →  body.commissionAmount               no (max 22)
 * vatFee                  →  body.tax                            no (max 22)
 * tranCode (msbTransCode) → body.msbTransCode                   YES (max 35)
 * tranSeq (msbTransSeq)   → body.msbTransSeq                    YES (max 65)
 * channel                 →  body.msbChannel                     no (max 35)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Response mapping:
 * Backend response header.id → transactionId (FT number)
 * Backend response header.status → status
 * ═══════════════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferMappingService {

    private final ResilientBackendCaller backendCaller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String OPERATION_ID = "createMsbAccountTransfer";

    // ─── Public: gọi transfer ─────────────────────────────────────────────────

    /**
     * Nhận input nội bộ → mapping → gọi backend createMsbAccountTransfer → trả kết quả.
     *
     * @param transferInput  JSON chứa thông tin giao dịch (flat)
     * @param headers        headers bổ sung (credentials, companyId...)
     * @return response đã mapping
     */
    public Map<String, Object> createTransfer(Map<String, Object> transferInput,
                                               Map<String, String> headers) {

        // 1. Mapping input → MsbAccountTransfer payload
        ObjectNode payload = buildPayload(transferInput);

        log.info("createMsbAccountTransfer: debit={}, credit={}, amount={}",
                transferInput.get("debitAccount"),
                transferInput.get("creditAccount"),
                transferInput.get("debitAmount"));

        // 2. Gọi backend (không có path params cho API này)
        RestResponse<JsonNode> response = backendCaller.call(
                OPERATION_ID,
                Map.of(),       // không có path params
                payload,
                headers
        );

        // 3. Mapping response
        return buildResponse(response, transferInput);
    }

    // ─── Mapping INPUT → Swagger Body ─────────────────────────────────────────

    private ObjectNode buildPayload(Map<String, Object> input) {
        ObjectNode root = objectMapper.createObjectNode();

        // ── Header (PayloadHeader) ────────────────────────────────────────────
        ObjectNode header = objectMapper.createObjectNode();
        header.putObject("override");
        header.putObject("audit");
        root.set("header", header);

        // ── Body (MsbAccountTransferBody) ─────────────────────────────────────
        ObjectNode body = objectMapper.createObjectNode();

        // Required fields
        body.put("debitCurrency", getString(input, "debitCurrency", "VND"));
        body.put("creditCurrency", getString(input, "creditCurrency", "VND"));
        body.put("msbTransCode", getString(input, "tranCode", "PAYOO001"));
        body.put("msbTransSeq", getString(input, "tranSeq", "000001"));

        // Account & Amount
        putIfNotEmpty(body, "debitAccount", getString(input, "debitAccount"));
        putIfNotEmpty(body, "debitAmount", getString(input, "debitAmount"));
        putIfNotEmpty(body, "creditAccount", getString(input, "creditAccount"));
        putIfNotEmpty(body, "creditAmount", getString(input, "creditAmount"));

        // Rate (dùng creditRate hoặc debitRate)
        String rate = getString(input, "creditRate");
        if (rate.isEmpty()) rate = getString(input, "debitRate");
        putIfNotEmpty(body, "customerRate", rate);

        // Fee
        putIfNotEmpty(body, "commissionAmount", getString(input, "serviceFee"));
        putIfNotEmpty(body, "tax", getString(input, "vatFee"));

        // Channel
        putIfNotEmpty(body, "msbChannel", getString(input, "channel"));

        // Description → paymentDetails[0].paymentDetail
        String description = getString(input, "description");
        if (!description.isEmpty()) {
            var detailNode = objectMapper.createObjectNode();
            detailNode.put("paymentDetail", truncate(description, 50));
            var detailsArray = objectMapper.createArrayNode();
            detailsArray.add(detailNode);
            body.set("paymentDetails", detailsArray);
        }

        root.set("body", body);
        return root;
    }

    // ─── Mapping RESPONSE → Output đơn giản ──────────────────────────────────

    private Map<String, Object> buildResponse(RestResponse<JsonNode> response,
                                               Map<String, Object> originalInput) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (response.isSuccess() && response.getBody() != null) {
            JsonNode respBody = response.getBody();

            result.put("transactionId", extractField(respBody, "/header/id"));
            result.put("status", extractField(respBody, "/header/status"));
            result.put("transactionStatus", extractField(respBody, "/header/transactionStatus"));
            result.put("debitAccount", getString(originalInput, "debitAccount"));
            result.put("creditAccount", getString(originalInput, "creditAccount"));
            result.put("amount", getString(originalInput, "debitAmount"));
            result.put("currency", getString(originalInput, "debitCurrency", "VND"));
            result.put("success", true);
        } else {
            result.put("transactionId", "");
            result.put("status", "FAILED");
            result.put("error", response.getErrorMessage() != null ? response.getErrorMessage() : "Backend error");
            result.put("success", false);
        }

        return result;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getString(Map<String, Object> map, String key) {
        return getString(map, key, "");
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        if (val == null) return defaultValue;
        String str = val.toString().trim();
        return str.isEmpty() ? defaultValue : str;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private void putIfNotEmpty(ObjectNode node, String field, String value) {
        if (value != null && !value.isEmpty()) {
            node.put(field, value);
        }
    }

    private String extractField(JsonNode root, String jsonPointer) {
        if (root == null) return "";
        JsonNode node = root.at(jsonPointer);
        return (node != null && !node.isMissingNode() && !node.isNull()) ? node.asText() : "";
    }
}
