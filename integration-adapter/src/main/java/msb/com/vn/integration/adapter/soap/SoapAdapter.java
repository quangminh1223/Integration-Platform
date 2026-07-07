package msb.com.vn.integration.adapter.soap;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * SOAP/XML adapter — handles outbound SOAP web service calls.
 * Wraps payload in SOAP envelope and sends to WSDL endpoint.
 */
@Slf4j
@Component
public class SoapAdapter implements IntegrationAdapter {

    private final RestTemplate restTemplate;

    public SoapAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "soap-adapter";
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.SOAP;
    }

    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        String endpoint = message.getHeader("X-Target-Endpoint");
        String soapAction = message.getHeader("X-SOAP-Action");

        if (endpoint == null) {
            throw new AdapterException(getName(), "Missing X-Target-Endpoint header",
                    message.getCorrelationId(), false);
        }

        // Build SOAP envelope
        String soapBody = buildSoapEnvelope(message.getPayload());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.set("SOAPAction", soapAction != null ? soapAction : "");
        headers.set("X-Correlation-ID", message.getCorrelationId());

        HttpEntity<String> entity = new HttpEntity<>(soapBody, headers);

        log.info("SOAP call: endpoint={}, soapAction={}, correlationId={}",
                endpoint, soapAction, message.getCorrelationId());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, String.class);

            IntegrationMessage responseMessage = IntegrationMessage.builder()
                    .messageId(message.getMessageId() + "-response")
                    .correlationId(message.getCorrelationId())
                    .source(endpoint)
                    .target(message.getSource())
                    .payload(response.getBody())
                    .contentType("XML")
                    .status(MessageStatus.DELIVERED)
                    .build();

            responseMessage.addHeader("X-Http-Status", String.valueOf(response.getStatusCode().value()));
            return responseMessage;

        } catch (Exception e) {
            throw new AdapterException(getName(),
                    "SOAP call failed: " + e.getMessage(),
                    message.getCorrelationId(), true, e);
        }
    }

    @Override
    public boolean supports(IntegrationMessage message) {
        return "SOAP".equalsIgnoreCase(message.getHeader("X-Protocol"));
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    private String buildSoapEnvelope(Object payload) {
        String body = payload != null ? payload.toString() : "";

        // If payload is already a SOAP envelope, return as-is
        if (body.contains("<soap:Envelope") || body.contains("<soapenv:Envelope")) {
            return body;
        }

        // Wrap in SOAP envelope
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <soap:Header/>
                    <soap:Body>
                        %s
                    </soap:Body>
                </soap:Envelope>
                """.formatted(body);
    }
}
