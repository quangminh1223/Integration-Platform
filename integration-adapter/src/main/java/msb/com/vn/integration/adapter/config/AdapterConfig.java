package msb.com.vn.integration.adapter.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Adapter module configuration.
 */
@Configuration
public class AdapterConfig {

    /**
     * Uses {@code setConnectTimeout}/{@code setReadTimeout}, which is the API on
     * {@code RestTemplateBuilder} in Spring Boot 3.2.5. The shorter {@code connectTimeout}/
     * {@code readTimeout} names only arrived in Spring Boot 3.4, so calling them here did not
     * compile against the version this project pins.
     */
    @Bean
    public RestTemplate integrationRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(5000))
                .setReadTimeout(Duration.ofMillis(15000))
                .build();
    }
}
