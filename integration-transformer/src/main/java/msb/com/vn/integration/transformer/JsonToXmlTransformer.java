package msb.com.vn.integration.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ContentType;
import msb.com.vn.integration.common.exception.TransformationException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.core.transformer.MessageTransformer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transforms JSON payload to XML format.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonToXmlTransformer implements MessageTransformer {

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;

    @Override
    public String getName() {
        return "json-to-xml";
    }

    @Override
    public ContentType getSourceType() {
        return ContentType.JSON;
    }

    @Override
    public ContentType getTargetType() {
        return ContentType.XML;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IntegrationMessage transform(IntegrationMessage message) {
        try {
            Object payload = message.getPayload();
            Map<String, Object> data;

            if (payload instanceof String str) {
                data = jsonMapper.readValue(str, Map.class);
            } else if (payload instanceof Map) {
                data = (Map<String, Object>) payload;
            } else {
                data = jsonMapper.convertValue(payload, Map.class);
            }

            String xml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);

            IntegrationMessage result = IntegrationMessage.builder()
                    .messageId(message.getMessageId())
                    .correlationId(message.getCorrelationId())
                    .source(message.getSource())
                    .target(message.getTarget())
                    .payload(xml)
                    .contentType(ContentType.XML.name())
                    .headers(message.getHeaders())
                    .properties(message.getProperties())
                    .flowId(message.getFlowId())
                    .status(message.getStatus())
                    .build();

            log.debug("JSON → XML transformation successful: correlationId={}", message.getCorrelationId());
            return result;

        } catch (Exception e) {
            throw new TransformationException(
                    "JSON to XML transformation failed: " + e.getMessage(),
                    message.getCorrelationId(), e);
        }
    }
}
