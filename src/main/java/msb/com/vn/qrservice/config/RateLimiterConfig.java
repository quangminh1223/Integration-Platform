package msb.com.vn.qrservice.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiter qrGenerateRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build();
        return registry.rateLimiter("qr-generate", config);
    }

    @Bean
    public RateLimiter qrParseRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build();
        return registry.rateLimiter("qr-parse", config);
    }

    @Bean
    public Bulkhead qrGenerateBulkhead(BulkheadRegistry registry) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(80)
                .maxWaitDuration(Duration.ofMillis(500))
                .build();
        return registry.bulkhead("qr-generate", config);
    }

    @Bean
    public Bulkhead qrParseBulkhead(BulkheadRegistry registry) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(80)
                .maxWaitDuration(Duration.ofMillis(500))
                .build();
        return registry.bulkhead("qr-parse", config);
    }
}
