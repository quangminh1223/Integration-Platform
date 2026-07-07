package msb.com.vn.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MSB Enterprise Integration Platform entry point.
 * Assembles all modules via component scanning.
 */
@SpringBootApplication(scanBasePackages = "msb.com.vn.integration")
@EnableAsync
@EnableScheduling
public class IntegrationPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationPlatformApplication.class, args);
    }
}
