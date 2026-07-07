package msb.com.vn.integration.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka adapter — publishes messages to Kafka topics.
 * Topic is determined from message header "X-Kafka-Topic".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAdapter implements IntegrationAdapter {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "kafka-adapter";
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.KAFKA;
    }

    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        String topic = message.getHeader("X-Kafka-Topic");
        if (topic == null) {
            throw new AdapterException(getName(), "Missing X-Kafka-Topic header",
                    message.getCorrelationId(), false);
        }

        String key = message.getCorrelationId();

        log.info("Kafka publish: topic={}, key={}, correlationId={}", topic, key, message.getCorrelationId());

        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, key, message.getPayload());

            // Wait for confirmation (with timeout)
            SendResult<String, Object> result = future.get(10, TimeUnit.SECONDS);

            IntegrationMessage responseMessage = IntegrationMessage.builder()
                    .messageId(message.getMessageId() + "-ack")
                    .correlationId(message.getCorrelationId())
                    .source("kafka:" + topic)
                    .payload(Map.of(
                            "topic", topic,
                            "partition", result.getRecordMetadata().partition(),
                            "offset", result.getRecordMetadata().offset()
                    ))
                    .status(MessageStatus.DELIVERED)
                    .build();

            return responseMessage;

        } catch (Exception e) {
            throw new AdapterException(getName(),
                    "Kafka publish failed: " + e.getMessage(),
                    message.getCorrelationId(), true, e);
        }
    }

    @Override
    public boolean supports(IntegrationMessage message) {
        return "KAFKA".equalsIgnoreCase(message.getHeader("X-Protocol"));
    }

    @Override
    public boolean isHealthy() {
        try {
            kafkaTemplate.getProducerFactory().createProducer().close();
            return true;
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return false;
        }
    }

    // Needed for Map.of usage in send method
    private static class Map {
        static java.util.Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
            return java.util.Map.of(k1, v1, k2, v2, k3, v3);
        }
    }
}
