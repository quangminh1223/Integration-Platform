package msb.com.vn.integration.adapter.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * RabbitMQ adapter — publishes messages to RabbitMQ exchanges/queues.
 * Supports direct, topic, and fanout exchanges.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqAdapter implements IntegrationAdapter {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "rabbitmq-adapter";
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.RABBITMQ;
    }

    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        String exchange = message.getHeader("X-RabbitMQ-Exchange");
        String routingKey = message.getHeader("X-RabbitMQ-RoutingKey");
        String queue = message.getHeader("X-RabbitMQ-Queue");

        if (exchange == null && queue == null) {
            throw new AdapterException(getName(),
                    "Missing X-RabbitMQ-Exchange or X-RabbitMQ-Queue header",
                    message.getCorrelationId(), false);
        }

        log.info("RabbitMQ publish: exchange={}, routingKey={}, correlationId={}",
                exchange, routingKey, message.getCorrelationId());

        try {
            String payload = serializePayload(message.getPayload());

            MessageProperties props = new MessageProperties();
            props.setCorrelationId(message.getCorrelationId());
            props.setMessageId(message.getMessageId());
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);

            // Add custom headers
            message.getHeaders().forEach((k, v) -> {
                if (!k.startsWith("X-RabbitMQ-")) {
                    props.setHeader(k, v);
                }
            });

            Message amqpMessage = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

            if (exchange != null) {
                rabbitTemplate.send(exchange, routingKey != null ? routingKey : "", amqpMessage);
            } else {
                rabbitTemplate.send(queue, amqpMessage);
            }

            return IntegrationMessage.builder()
                    .messageId(message.getMessageId() + "-ack")
                    .correlationId(message.getCorrelationId())
                    .source("rabbitmq:" + (exchange != null ? exchange : queue))
                    .payload(Map.of(
                            "exchange", exchange != null ? exchange : "",
                            "routingKey", routingKey != null ? routingKey : "",
                            "status", "PUBLISHED"
                    ))
                    .status(MessageStatus.DELIVERED)
                    .build();

        } catch (Exception e) {
            throw new AdapterException(getName(),
                    "RabbitMQ publish failed: " + e.getMessage(),
                    message.getCorrelationId(), true, e);
        }
    }

    @Override
    public boolean supports(IntegrationMessage message) {
        return "RABBITMQ".equalsIgnoreCase(message.getHeader("X-Protocol"));
    }

    @Override
    public boolean isHealthy() {
        try {
            rabbitTemplate.execute(channel -> {
                channel.basicQos(1);
                return null;
            });
            return true;
        } catch (Exception e) {
            log.warn("RabbitMQ health check failed: {}", e.getMessage());
            return false;
        }
    }

    private String serializePayload(Object payload) throws Exception {
        if (payload instanceof String str) {
            return str;
        }
        return objectMapper.writeValueAsString(payload);
    }
}
