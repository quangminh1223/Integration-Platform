package msb.com.vn.qrservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.response.ApiResponse;
import msb.com.vn.qrservice.dto.request.Iso8583BuildRequest;
import msb.com.vn.qrservice.dto.request.Iso8583ParseRequest;
import msb.com.vn.qrservice.dto.response.Iso8583BuildResponse;
import msb.com.vn.qrservice.dto.response.Iso8583ParseResponse;
import msb.com.vn.qrservice.service.Iso8583Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/iso8583")
@RequiredArgsConstructor
@Tag(name = "ISO8583", description = "API xử lý bản tin ISO8583")
public class Iso8583Controller {

    private final Iso8583Service iso8583Service;

    @PostMapping("/parse")
    @Operation(
        summary = "Parse bản tin ISO8583",
        description = "Nhận bản tin ISO8583 dạng hex string, trả về các field đã được parse"
    )
    public ResponseEntity<ApiResponse<Iso8583ParseResponse>> parseMessage(
            @Valid @RequestBody Iso8583ParseRequest request) {

        log.info("Nhận request parse ISO8583, requestedBy={}", request.getRequestedBy());
        Iso8583ParseResponse response = iso8583Service.parseMessage(request);
        return ResponseEntity.ok(ApiResponse.success("Parse bản tin ISO8583 thành công", response));
    }

    @PostMapping("/build")
    @Operation(
        summary = "Build bản tin ISO8583",
        description = "Nhận MTI và danh sách field, trả về bản tin ISO8583 dạng hex string"
    )
    public ResponseEntity<ApiResponse<Iso8583BuildResponse>> buildMessage(
            @Valid @RequestBody Iso8583BuildRequest request) {

        log.info("Nhận request build ISO8583: mti={}, requestedBy={}", request.getMti(), request.getRequestedBy());
        Iso8583BuildResponse response = iso8583Service.buildMessage(request);
        return ResponseEntity.ok(ApiResponse.success("Build bản tin ISO8583 thành công", response));
    }
}
