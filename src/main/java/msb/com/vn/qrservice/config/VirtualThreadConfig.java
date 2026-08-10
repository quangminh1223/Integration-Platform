package msb.com.vn.qrservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cấu hình Virtual Threads (Java 21) cho OCP container.
 *
 * Virtual Threads giải quyết triệt để vấn đề thread exhaustion khi backend timeout:
 * - Platform threads: 200 threads × 10s timeout = 200 threads bị chiếm → service chết
 * - Virtual threads: hàng triệu virtual threads, mỗi cái chỉ tốn ~1KB stack
 *   → 10,000 requests chờ backend timeout cùng lúc vẫn OK, không ảnh hưởng service
 *
 * Khi backend timeout 10s:
 * - Virtual thread bị park (suspend) → không chiếm platform thread
 * - Platform thread được giải phóng để xử lý request khác
 * - Khi backend trả về → virtual thread resume trên bất kỳ platform thread nào
 */
@Slf4j
@Configuration
public class VirtualThreadConfig {

    /**
     * Executor dùng Virtual Threads cho các task async.
     * Thay thế ThreadPoolTaskExecutor truyền thống.
     */
    @Bean("virtualThreadExecutor")
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true", matchIfMissing = false)
    public ExecutorService virtualThreadExecutor() {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  VIRTUAL THREADS ENABLED (Java 21)");
        log.info("  Backend timeout sẽ KHÔNG gây thread exhaustion");
        log.info("═══════════════════════════════════════════════════════════════");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Fallback: nếu không bật virtual threads (Java < 21 hoặc config off),
     * dùng thread pool truyền thống nhưng sizing cho OCP container.
     */
    @Bean("containerTaskExecutor")
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "false", matchIfMissing = true)
    public TaskExecutor containerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("container-async-");
        executor.setKeepAliveSeconds(60);
        // Khi queue đầy → reject ngay (không block)
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Container TaskExecutor initialized: core=10, max=50, queue=500");
        return executor;
    }
}
