package msb.com.vn.qrservice.queue;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Quản lý tập trung tất cả ManagedQueue trong ứng dụng.
 *
 * Trách nhiệm:
 * - Tạo & đăng ký queue (register)
 * - Theo dõi metrics tất cả queue (getAllStats)
 * - Graceful shutdown toàn bộ queue khi app tắt
 * - Cảnh báo khi queue gần đầy
 *
 * <h3>Sử dụng:</h3>
 * <pre>
 * // Trong service, đăng ký queue qua manager
 * ManagedQueue&lt;String&gt; logQueue = queueManager.register(
 *     "transaction-log", 10000, 2, 100,
 *     item -&gt; writeLog(item)
 * );
 * logQueue.offer("data");
 * </pre>
 */
@Slf4j
@Component
public class QueueManager {

    private final Map<String, ManagedQueue<?>> queues = new ConcurrentHashMap<>();

    /** TTL mặc định: 3 phút */
    public static final long DEFAULT_TTL_MILLIS = 3 * 60 * 1000L;

    /**
     * Tạo và đăng ký queue với cấu hình đầy đủ (TTL + Dead Letter Queue).
     *
     * @param name               tên định danh queue (duy nhất)
     * @param capacity           sức chứa tối đa
     * @param workerThreads      số thread xử lý
     * @param batchSize          số item drain mỗi lần
     * @param ttlMillis          TTL (ms) — message quá hạn sẽ vào DLQ. 0 = không giới hạn.
     * @param consumer           hàm xử lý item bình thường
     * @param deadLetterConsumer hàm xử lý dead letter (null = chỉ log + drop)
     * @param <T>                kiểu item
     * @return ManagedQueue đã start
     */
    public <T> ManagedQueue<T> register(String name, int capacity, int workerThreads,
                                        int batchSize, long ttlMillis,
                                        Consumer<T> consumer,
                                        Consumer<ManagedQueue.DeadLetter<T>> deadLetterConsumer) {
        if (queues.containsKey(name)) {
            throw new IllegalArgumentException("Queue đã tồn tại: " + name);
        }
        ManagedQueue<T> queue = new ManagedQueue<>(name, capacity, workerThreads, batchSize,
                ttlMillis, consumer, deadLetterConsumer);
        queue.start();
        queues.put(name, queue);
        log.info("QueueManager: đã đăng ký queue [{}] (ttl={}ms, DLQ={})",
                name, ttlMillis, deadLetterConsumer != null ? "yes" : "no");
        return queue;
    }

    /**
     * Đăng ký queue đơn giản — TTL mặc định 3 phút, không có DLQ handler riêng.
     */
    public <T> ManagedQueue<T> register(String name, int capacity, int workerThreads,
                                        int batchSize, Consumer<T> consumer) {
        return register(name, capacity, workerThreads, batchSize,
                DEFAULT_TTL_MILLIS, consumer, null);
    }

    /**
     * Lấy queue theo tên.
     */
    @SuppressWarnings("unchecked")
    public <T> ManagedQueue<T> getQueue(String name) {
        return (ManagedQueue<T>) queues.get(name);
    }

    /**
     * Lấy metrics tất cả queue.
     */
    public List<ManagedQueue.QueueStats> getAllStats() {
        return queues.values().stream()
                .map(ManagedQueue::getStats)
                .toList();
    }

    /**
     * Lấy metrics 1 queue.
     */
    public ManagedQueue.QueueStats getStats(String name) {
        ManagedQueue<?> queue = queues.get(name);
        return queue != null ? queue.getStats() : null;
    }

    /**
     * Tổng quan toàn bộ queue (dùng cho health check / monitoring).
     */
    public Map<String, Object> getOverview() {
        long totalEnqueued = 0, totalProcessed = 0, totalExpired = 0, totalDropped = 0, totalErrors = 0;
        int totalQueued = 0;
        boolean hasWarning = false;

        for (ManagedQueue<?> q : queues.values()) {
            ManagedQueue.QueueStats s = q.getStats();
            totalEnqueued += s.enqueued();
            totalProcessed += s.processed();
            totalExpired += s.expired();
            totalDropped += s.dropped();
            totalErrors += s.errors();
            totalQueued += s.currentSize();
            if (s.usagePercent() > 80) hasWarning = true;
        }

        return Map.of(
                "totalQueues", queues.size(),
                "queueNames", queues.keySet(),
                "totalEnqueued", totalEnqueued,
                "totalProcessed", totalProcessed,
                "totalExpiredToDLQ", totalExpired,
                "totalDropped", totalDropped,
                "totalErrors", totalErrors,
                "totalItemsInQueues", totalQueued,
                "status", hasWarning ? "WARNING" : "HEALTHY"
        );
    }

    /**
     * Số queue đang quản lý.
     */
    public int getQueueCount() {
        return queues.size();
    }

    @PreDestroy
    public void shutdownAll() {
        log.info("QueueManager: shutting down {} queues...", queues.size());
        queues.values().forEach(ManagedQueue::shutdown);
        queues.clear();
        log.info("QueueManager: tất cả queue đã shutdown");
    }
}
