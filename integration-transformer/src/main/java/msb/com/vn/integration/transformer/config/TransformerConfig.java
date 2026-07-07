package msb.com.vn.integration.transformer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for transformer module beans.
 */
@Configuration
public class TransformerConfig {

    @Bean
    public XmlMapper xmlMapper() {
        return new XmlMapper();
    }

    @Bean
    public ObjectMapper integrationObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
