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
 * Transforms XML payload to JSON format.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmlToJsonTransformer implements MessageTransformer {

    private final XmlMapper xmlMapper;
    private final ObjectMapper jsonMapper;

    @Override
    public String getName() {
        return "xml-to-json";
    }

    @Override
    public ContentType getSourceType() {
        return ContentType.XML;
    }

    @Override
    public ContentType getTargetType() {
        return ContentType.JSON;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IntegrationMessage transform(IntegrationMessage message) {
        try {
            String xmlPayload = message.getPayload().toString();
            Map<String, Object> data = xmlMapper.readValue(xmlPayload, Map.class);
            String json = jsonMapper.writeValueAsString(data);

            IntegrationMessage result = IntegrationMessage.builder()
                    .messageId(message.getMessageId())
                    .correlationId(message.getCorrelationId())
                    .source(message.getSource())
                    .target(message.getTarget())
                    .payload(json)
                    .contentType(ContentType.JSON.name())
                    .headers(message.getHeaders())
                    .properties(message.getProperties())
                    .flowId(message.getFlowId())
                    .status(message.getStatus())
                    .build();

            log.debug("XML → JSON transformation successful: correlationId={}", message.getCorrelationId());
            return result;

        } catch (Exception e) {
            throw new TransformationException(
                    "XML to JSON transformation failed: " + e.getMessage(),
                    message.getCorrelationId(), e);
        }
    }
}
