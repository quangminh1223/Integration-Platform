package msb.com.vn.qrservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.qr-executor.core-pool-size:20}")
    private int corePoolSize;

    @Value("${async.qr-executor.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${async.qr-executor.queue-capacity:200}")
    private int queueCapacity;

    @Value("${async.qr-executor.thread-name-prefix:qr-async-}")
    private String threadNamePrefix;

    @Value("${async.qr-executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean(name = "qrTaskExecutor")
    public Executor qrTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setAllowCoreThreadTimeOut(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("QR Task Executor khởi tạo: core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean(name = "ioTaskExecutor")
    public Executor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(30);
        executor.setMaxPoolSize(80);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("qr-io-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("IO Task Executor khởi tạo: core=30, max=80, queue=500");
        return executor;
    }
}
