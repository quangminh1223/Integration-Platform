package msb.com.vn.dsign;

import msb.com.vn.dsign.config.DSignConfig;
import msb.com.vn.dsign.config.HsmConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for the D-Sign module.
 *
 * Activates when {@code dsign.enabled=true} (default: true).
 * Scans the msb.com.vn.dsign package for Spring components and
 * binds configuration properties prefixed with "dsign".
 */
@Configuration
@ConditionalOnProperty(prefix = "dsign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({DSignConfig.class, HsmConfig.class})
@ComponentScan(basePackages = "msb.com.vn.dsign")
public class DSignAutoConfiguration {
}
