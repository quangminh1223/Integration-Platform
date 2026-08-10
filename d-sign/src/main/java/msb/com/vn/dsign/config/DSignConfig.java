package msb.com.vn.dsign.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Root configuration properties for the D-Sign module.
 * Binds all properties under the "dsign" prefix.
 */
@Data
@ConfigurationProperties(prefix = "dsign")
public class DSignConfig {

    /**
     * Whether the D-Sign module is enabled. Default: true.
     */
    private boolean enabled = true;

    /**
     * Algorithm configuration.
     */
    private Algorithms algorithms = new Algorithms();

    /**
     * CA provider configuration.
     */
    private Ca ca = new Ca();

    /**
     * Audit configuration.
     */
    private Audit audit = new Audit();

    /**
     * Resilience configuration for HSM and CA interactions.
     */
    private Resilience resilience = new Resilience();

    @Data
    public static class Algorithms {
        /**
         * List of supported signing algorithms.
         */
        private List<String> supported = List.of("SHA256withRSA", "SHA512withRSA");
    }

    @Data
    public static class Ca {
        /**
         * The active CA provider identifier for the current environment.
         */
        private String activeProvider = "vnca";

        /**
         * Map of CA provider configurations keyed by provider name.
         */
        private Map<String, CaProviderConfig> providers = Map.of();
    }

    @Data
    public static class CaProviderConfig {
        /**
         * Base URL of the CA provider API.
         */
        private String baseUrl;

        /**
         * API key for authenticating with the CA provider.
         */
        private String apiKey;

        /**
         * Certificate validation method: OCSP or CRL.
         */
        private String validationMethod = "OCSP";
    }

    @Data
    public static class Audit {
        /**
         * Kafka topic for audit events.
         */
        private String kafkaTopic = "dsign.audit.events";

        /**
         * Number of days to retain audit records.
         */
        private int retentionDays = 365;
    }

    @Data
    public static class Resilience {
        /**
         * HSM resilience settings.
         */
        private ResilienceSettings hsm = new ResilienceSettings(50, 5);

        /**
         * CA provider resilience settings.
         */
        private ResilienceSettings ca = new ResilienceSettings(50, 10);
    }

    @Data
    public static class ResilienceSettings {
        /**
         * Failure rate threshold (%) to open the circuit breaker.
         */
        private int circuitBreakerFailureRate;

        /**
         * Timeout in seconds for operations.
         */
        private int timeoutSeconds;

        public ResilienceSettings() {
            this.circuitBreakerFailureRate = 50;
            this.timeoutSeconds = 5;
        }

        public ResilienceSettings(int circuitBreakerFailureRate, int timeoutSeconds) {
            this.circuitBreakerFailureRate = circuitBreakerFailureRate;
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
