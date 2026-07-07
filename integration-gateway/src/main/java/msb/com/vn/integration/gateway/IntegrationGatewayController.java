package msb.com.vn.integration.gateway;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.IntegrationResponse;
import msb.com.vn.integration.gateway.dto.IntegrationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Main API Gateway entry point.
 * Receives external requests, validates, and dispatches to the integration engine.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
public class IntegrationGatewayController {

    private final GatewayService gatewayService;

    /**
     * Generic integration endpoint — supports any flow via request body configuration.
     */
    @PostMapping("/execute")
    public ResponseEntity<IntegrationResponse<Object>> execute(
            @Valid @RequestBody IntegrationRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("Integration request received: flowId={}, correlationId={}",
                request.getFlowId(), correlationId);

        IntegrationMessage message = IntegrationMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .correlationId(correlationId)
                .source(request.getSource())
                .target(request.getTarget())
                .payload(request.getPayload())
                .contentType(request.getContentType())
                .flowId(request.getFlowId())
                .headers(request.getHeaders() != null ? request.getHeaders() : new java.util.HashMap<>())
                .build();

        IntegrationMessage result = gatewayService.process(message);

        IntegrationResponse<Object> response = IntegrationResponse.success(
                correlationId, result.getPayload());

        return ResponseEntity.ok(response);
    }

    /**
     * Health and flow discovery endpoint.
     */
    @GetMapping("/flows")
    public ResponseEntity<IntegrationResponse<Object>> listFlows() {
        return ResponseEntity.ok(
                IntegrationResponse.success("system", gatewayService.getAvailableFlows()));
    }
}
