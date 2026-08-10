package msb.com.vn.qrservice.iso8583.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.iso8583.dto.Iso8583SendRequest;
import msb.com.vn.qrservice.iso8583.dto.Iso8583SendResponse;
import msb.com.vn.qrservice.iso8583.dto.Iso8583SocketStatus;
import msb.com.vn.qrservice.iso8583.service.Iso8583SocketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API điều khiển và giám sát socket ISO8583.
 * Dùng để kích hoạt luồng outbound và kiểm tra trạng thái hai kênh.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/iso8583/socket")
@RequiredArgsConstructor
@Tag(name = "ISO8583 Socket", description = "Gửi/nhận bản tin ISO8583 qua TCP socket")
public class Iso8583SocketController {

    private final Iso8583SocketService socketService;

    @PostMapping("/send")
    @Operation(summary = "Gửi bản tin ISO8583 qua socket outbound",
            description = "Dựng bản tin từ MTI + danh sách field, gửi tới đối tác và chờ response")
    public ResponseEntity<ApiResponse<Iso8583SendResponse>> send(
            @Valid @RequestBody Iso8583SendRequest request) {

        log.info("Yêu cầu gửi ISO8583 outbound: mti={}, fields={}",
                request.getMti(), request.getFields().keySet());

        Iso8583SendResponse response = socketService.send(request);
        return ResponseEntity.ok(ApiResponse.success("Gửi bản tin ISO8583 thành công", response));
    }

    @PostMapping("/send-raw")
    @Operation(summary = "Gửi bản tin ISO8583 dạng hex",
            description = "Gửi bản tin đã đóng gói sẵn dạng hex string qua socket outbound")
    public ResponseEntity<ApiResponse<Iso8583SendResponse>> sendRaw(
            @RequestBody Map<String, String> body) {

        String hexMessage = body.get("hexMessage");
        if (hexMessage == null || hexMessage.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_REQUEST", "Thiếu trường 'hexMessage'"));
        }

        Iso8583SendResponse response = socketService.sendRawHex(hexMessage, body.get("correlationId"));
        return ResponseEntity.ok(ApiResponse.success("Gửi bản tin raw thành công", response));
    }

    @GetMapping("/status")
    @Operation(summary = "Trạng thái socket ISO8583",
            description = "Trả về trạng thái kênh inbound (port lắng nghe) và outbound (kết nối ra)")
    public ResponseEntity<ApiResponse<Iso8583SocketStatus>> status() {
        return ResponseEntity.ok(ApiResponse.success(socketService.getStatus()));
    }
}
