package msb.com.vn.integration.adapter.rest;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * REST/HTTP adapter — handles outbound REST API calls.
 * Supports configurable endpoints, headers, and timeouts.
 */
@Slf4j
@Component
public class RestAdapter implements IntegrationAdapter {

    private final RestTemplate restTemplate;

    public RestAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "rest-adapter";
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.REST;
    }

    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        String endpoint = message.getHeader("X-Target-Endpoint");
        String method = message.getHeader("X-Http-Method");
        if (endpoint == null) {
            throw new AdapterException(getName(), "Missing X-Target-Endpoint header",
                    message.getCorrelationId(), false);
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method != null ? method.toUpperCase() : "POST");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", message.getCorrelationId());

        // Forward custom headers
        message.getHeaders().entrySet().stream()
                .filter(e -> !e.getKey().startsWith("X-"))
                .forEach(e -> headers.set(e.getKey(), e.getValue()));

        HttpEntity<Object> entity = new HttpEntity<>(message.getPayload(), headers);

        log.info("REST call: {} {} correlationId={}", httpMethod, endpoint, message.getCorrelationId());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint, httpMethod, entity, Map.class);

            IntegrationMessage responseMessage = IntegrationMessage.builder()
                    .messageId(message.getMessageId() + "-response")
                    .correlationId(message.getCorrelationId())
                    .source(endpoint)
                    .target(message.getSource())
                    .payload(response.getBody())
                    .contentType("JSON")
                    .status(MessageStatus.DELIVERED)
                    .build();

            responseMessage.addHeader("X-Http-Status", String.valueOf(response.getStatusCode().value()));
            return responseMessage;

        } catch (Exception e) {
            throw new AdapterException(getName(),
                    "REST call failed: " + e.getMessage(),
                    message.getCorrelationId(), true, e);
        }
    }

    @Override
    public boolean supports(IntegrationMessage message) {
        String protocol = message.getHeader("X-Protocol");
        return protocol == null || "REST".equalsIgnoreCase(protocol);
    }

    @Override
    public boolean isHealthy() {
        return true; // REST is stateless — always ready
    }
}
