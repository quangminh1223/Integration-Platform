package msb.com.vn.dsign.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * HSM-specific configuration properties.
 * Binds all properties under the "dsign.hsm" prefix.
 */
@Data
@ConfigurationProperties(prefix = "dsign.hsm")
public class HsmConfig {

    /**
     * Number of connections in the HSM connection pool.
     */
    private int poolSize = 10;

    /**
     * Maximum time (ms) to wait for an HSM connection from the pool.
     */
    private long connectionTimeoutMs = 5000;

    /**
     * Time (ms) after which an idle HSM connection is eligible for eviction.
     */
    private long idleTimeoutMs = 60000;

    /**
     * Maximum number of signing requests to queue when the pool is exhausted.
     * Requests beyond this limit receive an HTTP 503 response.
     */
    private int maxQueueDepth = 50;

    /**
     * List of HSM slot configurations.
     */
    private List<SlotConfig> slots = new ArrayList<>();

    @Data
    public static class SlotConfig {
        /**
         * Slot identifier.
         */
        private String id;

        /**
         * PIN for the slot (should be externalized via environment variables).
         */
        private String pin;
    }
}
