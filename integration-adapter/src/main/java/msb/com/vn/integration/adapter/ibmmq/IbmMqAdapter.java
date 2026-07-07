package msb.com.vn.integration.adapter.ibmmq;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * IBM MQ adapter — sends/receives messages via IBM WebSphere MQ (JMS).
 * Used for enterprise messaging with legacy banking systems.
 */
@Slf4j
@Component
public class IbmMqAdapter implements IntegrationAdapter {

    private final JmsTemplate jmsTemplate;

    public IbmMqAdapter(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public String getName() {
        return "ibmmq-adapter";
    }

    @Override
    public ProtocolType getProtocol() {
        return ProtocolType.IBM_MQ;
    }

    @Override
    public IntegrationMessage send(IntegrationMessage message) {
        String queueName = message.getHeader("X-IBM-MQ-Queue");

        if (queueName == null) {
            throw new AdapterException(getName(), "Missing X-IBM-MQ-Queue header",
                    message.getCorrelationId(), false);
        }

        log.info("IBM MQ send: queue={}, correlationId={}", queueName, message.getCorrelationId());

        try {
            String payload = message.getPayload() != null ? message.getPayload().toString() : "";

            jmsTemplate.convertAndSend(queueName, payload, jmsMessage -> {
                jmsMessage.setJMSCorrelationID(message.getCorrelationId());
                jmsMessage.setStringProperty("MessageId", message.getMessageId());
                jmsMessage.setStringProperty("Source", message.getSource());

                // Forward headers as JMS properties
                message.getHeaders().forEach((k, v) -> {
                    if (!k.startsWith("X-IBM-MQ-")) {
                        try {
                            jmsMessage.setStringProperty(k.replace("-", "_"), v);
                        } catch (Exception ignored) {
                        }
                    }
                });

                return jmsMessage;
            });

            return IntegrationMessage.builder()
                    .messageId(message.getMessageId() + "-sent")
                    .correlationId(message.getCorrelationId())
                    .source("ibmmq:" + queueName)
                    .payload(Map.of(
                            "queue", queueName,
                            "status", "SENT"
                    ))
                    .status(MessageStatus.DELIVERED)
                    .build();

        } catch (Exception e) {
            throw new AdapterException(getName(),
                    "IBM MQ send failed: " + e.getMessage(),
                    message.getCorrelationId(), true, e);
        }
    }

    @Override
    public boolean supports(IntegrationMessage message) {
        return "IBM_MQ".equalsIgnoreCase(message.getHeader("X-Protocol"));
    }

    @Override
    public boolean isHealthy() {
        try {
            jmsTemplate.getConnectionFactory();
            return true;
        } catch (Exception e) {
            log.warn("IBM MQ health check failed: {}", e.getMessage());
            return false;
        }
    }
}
