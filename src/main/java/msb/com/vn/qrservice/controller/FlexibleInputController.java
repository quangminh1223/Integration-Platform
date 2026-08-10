package msb.com.vn.qrservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.http.FlexibleBody;
import msb.com.vn.qrservice.common.http.HttpInputResolver;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.dto.request.TransferToIso8583Request;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Demo controller — nhận body ở bất kỳ dạng nào và tự động normalize.
 *
 * Client có thể gửi:
 *   1. JSON object bình thường
 *   2. JSON string (escaped)
 *   3. String chứa JSON bị escape 2 lần
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/flexible")
@RequiredArgsConstructor
@Tag(name = "Flexible Input", description = "API nhận body linh hoạt — JSON object, JSON string, escaped JSON")
public class FlexibleInputController {

    // ─── Endpoint 1: nhận FlexibleBody → map sang POJO cụ thể ───────────────

    @PostMapping("/transfer")
    @Operation(
        summary = "Nhận transfer request ở bất kỳ dạng nào",
        description = """
            Body có thể là:
            - JSON object: {"creditAccount":"80000002233","debitAmount":"98000",...}
            - JSON string: "{\\"creditAccount\\":\\"80000002233\\",...}"
            - Double-escaped: "\\"{...}\\""
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = {
                @ExampleObject(name = "JSON object",
                    value = """
                        {
                          "creditAccount": "80000002233",
                          "creditAmount": "98000",
                          "creditCurrency": "VND",
                          "creditRate": "10000000",
                          "debitAccount": "VND1217000011000",
                          "debitAmount": "98000",
                          "debitCurrency": "VND",
                          "debitRate": "10000000",
                          "description": "-704869-test timeout esb ok",
                          "vatFee": "0",
                          "serviceFee": "0",
                          "feeOwn": ""
                        }
                        """),
                @ExampleObject(name = "JSON string (escaped)",
                    value = """
                        "{\\"creditAccount\\":\\"80000002233\\",\\"debitAmount\\":\\"98000\\"}"
                        """)
            })
        )
    )
    public ResponseEntity<ApiResponse<TransferToIso8583Request>> processTransfer(
            @RequestBody FlexibleBody body) {

        log.info("Nhận request dạng: {}", body);

        // Tự động convert sang POJO — không quan tâm client gửi dạng gì
        TransferToIso8583Request request = HttpInputResolver.resolve(body, TransferToIso8583Request.class);

        log.info("Đã parse thành công: creditAccount={}, debitAmount={}",
                request.getCreditAccount(), request.getDebitAmount());

        return ResponseEntity.ok(ApiResponse.success("Parse thành công", request));
    }

    // ─── Endpoint 2: nhận FlexibleBody → trả về JsonNode (dynamic) ──────────

    @PostMapping("/raw")
    @Operation(
        summary = "Nhận body bất kỳ, trả về JsonNode đã normalize",
        description = "Hữu ích khi chưa biết schema của body trước"
    )
    public ResponseEntity<ApiResponse<JsonNode>> processRaw(
            @RequestBody FlexibleBody body) {

        JsonNode node = HttpInputResolver.resolveToNode(body);

        log.info("Body type: isObject={}, isArray={}, isText={}",
                body.isJsonObject(), body.isJsonArray(), body.isPlainText());

        return ResponseEntity.ok(ApiResponse.success("Đã normalize body", node));
    }

    // ─── Endpoint 3: nhận FlexibleBody → Map<String, Object> ────────────────

    @PostMapping("/map")
    @Operation(
        summary = "Nhận body bất kỳ, trả về Map<String, Object>",
        description = "Body phải là JSON object"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> processAsMap(
            @RequestBody FlexibleBody body) {

        Map<String, Object> map = HttpInputResolver.resolveToMap(body);

        return ResponseEntity.ok(ApiResponse.success("Đã convert sang Map", map));
    }
}
