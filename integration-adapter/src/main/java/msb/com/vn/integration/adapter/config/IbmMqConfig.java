package msb.com.vn.integration.adapter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;

import jakarta.jms.ConnectionFactory;

/**
 * IBM MQ configuration — only loaded when ibm-mq.enabled=true.
 * Requires IBM MQ client dependency in classpath.
 */
@Configuration
@ConditionalOnProperty(prefix = "integration.ibm-mq", name = "enabled", havingValue = "true")
public class IbmMqConfig {

    @Bean
    public JmsTemplate ibmMqJmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setReceiveTimeout(5000);
        return template;
    }
}
