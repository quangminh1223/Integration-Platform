package msb.com.vn.qrservice.queue;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Một queue async tự quản lý — đóng gói pattern: bounded queue + worker thread + metrics + TTL/DLQ.
 *
 * <h3>Đặc điểm:</h3>
 * - Bounded queue → không OOM
 * - offer() non-blocking → không block business thread
 * - Worker thread riêng (daemon, priority thấp)
 * - Batch drain → giảm context switch
 * - <b>TTL</b>: message nằm trong queue quá lâu (mặc định 3 phút) → chuyển sang Dead Letter Queue
 * - <b>Dead Letter Queue (DLQ)</b>: message hết hạn / xử lý lỗi → đẩy sang DLQ handler
 * - Metrics: enqueued, processed, dropped, expired, errors
 *
 * <h3>Luồng TTL:</h3>
 * <pre>
 * offer(item) → wrap kèm enqueueTime → queue
 *                                        │
 *                          worker poll ──┤
 *                                        ▼
 *                          age > TTL (3 phút)?
 *                          ├── YES → Dead Letter Queue (deadLetterConsumer)
 *                          └── NO  → xử lý bình thường (itemConsumer)
 * </pre>
 *
 * @param <T> kiểu phần tử trong queue
 */
@Slf4j
public class ManagedQueue<T> {

    private final String name;
    private final int capacity;
    private final int workerThreads;
    private final int batchSize;
    private final long ttlMillis;
    private final Consumer<T> itemConsumer;
    private final Consumer<DeadLetter<T>> deadLetterConsumer;

    private BlockingQueue<TimedItem<T>> queue;
    private ExecutorService workerPool;
    private volatile boolean running = false;

    // Metrics
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);
    private final AtomicLong totalExpired = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    /**
     * @param name               tên định danh
     * @param capacity           sức chứa tối đa
     * @param workerThreads      số thread xử lý
     * @param batchSize          số item drain mỗi lần
     * @param ttlMillis          TTL (ms) — message nằm trong queue quá thời gian này sẽ vào DLQ
     * @param itemConsumer       hàm xử lý item bình thường
     * @param deadLetterConsumer hàm xử lý dead letter (message hết hạn / lỗi). Có thể null.
     */
    public ManagedQueue(String name, int capacity, int workerThreads, int batchSize,
                        long ttlMillis,
                        Consumer<T> itemConsumer,
                        Consumer<DeadLetter<T>> deadLetterConsumer) {
        this.name = name;
        this.capacity = capacity;
        this.workerThreads = workerThreads;
        this.batchSize = batchSize;
        this.ttlMillis = ttlMillis;
        this.itemConsumer = itemConsumer;
        this.deadLetterConsumer = deadLetterConsumer;
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    public synchronized void start() {
        if (running) return;
        queue = new LinkedBlockingQueue<>(capacity);
        workerPool = Executors.newFixedThreadPool(workerThreads, r -> {
            Thread t = new Thread(r, name + "-worker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        running = true;
        for (int i = 0; i < workerThreads; i++) {
            workerPool.submit(this::workerLoop);
        }
        log.info("ManagedQueue [{}] started: capacity={}, workers={}, batchSize={}, ttl={}ms",
                name, capacity, workerThreads, batchSize, ttlMillis);
    }

    public synchronized void shutdown() {
        running = false;
        drainRemaining();
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("ManagedQueue [{}] shutdown. enqueued={}, processed={}, expired={}, dropped={}, errors={}",
                name, totalEnqueued.get(), totalProcessed.get(), totalExpired.get(),
                totalDropped.get(), totalErrors.get());
    }

    // ─── Offer (non-blocking) ─────────────────────────────────────────────────

    /**
     * Thêm item vào queue — non-blocking. Trả false nếu queue đầy (item bị drop).
     * Item được gắn timestamp enqueue để tính TTL.
     */
    public boolean offer(T item) {
        if (!running) return false;
        boolean offered = queue.offer(new TimedItem<>(item, System.currentTimeMillis()));
        if (offered) {
            totalEnqueued.incrementAndGet();
        } else {
            long dropped = totalDropped.incrementAndGet();
            if (dropped % 100 == 1) {
                log.warn("ManagedQueue [{}] full! Dropped {} items", name, dropped);
            }
        }
        return offered;
    }

    // ─── Worker loop ──────────────────────────────────────────────────────────

    private void workerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                List<TimedItem<T>> batch = new ArrayList<>(batchSize);
                TimedItem<T> first = queue.poll(500, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - 1);
                }
                for (TimedItem<T> timed : batch) {
                    dispatch(timed);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("ManagedQueue [{}] worker error: {}", name, e.getMessage());
            }
        }
    }

    /**
     * Quyết định: item còn hạn → xử lý; hết hạn (TTL) → đẩy sang DLQ.
     */
    private void dispatch(TimedItem<T> timed) {
        long age = System.currentTimeMillis() - timed.enqueueTime();

        // Kiểm tra TTL: nằm trong queue quá lâu → Dead Letter
        if (ttlMillis > 0 && age > ttlMillis) {
            handleDeadLetter(timed.item(), age,
                    "Message expired sau " + age + "ms (TTL=" + ttlMillis + "ms)", null);
            return;
        }

        // Xử lý bình thường
        try {
            itemConsumer.accept(timed.item());
            totalProcessed.incrementAndGet();
        } catch (Exception e) {
            totalErrors.incrementAndGet();
            log.error("ManagedQueue [{}] consumer error: {}", name, e.getMessage());
            // Xử lý lỗi cũng đẩy sang DLQ
            handleDeadLetter(timed.item(), age, "Consumer error: " + e.getMessage(), e);
        }
    }

    /**
     * Đẩy message sang Dead Letter Queue.
     */
    private void handleDeadLetter(T item, long age, String reason, Throwable cause) {
        totalExpired.incrementAndGet();
        log.warn("ManagedQueue [{}] → DLQ: {} (age={}ms)", name, reason, age);

        if (deadLetterConsumer != null) {
            try {
                deadLetterConsumer.accept(new DeadLetter<>(item, name, reason, age,
                        System.currentTimeMillis(), cause));
            } catch (Exception e) {
                log.error("ManagedQueue [{}] dead letter consumer error: {}", name, e.getMessage());
            }
        }
    }

    private void drainRemaining() {
        if (queue == null) return;
        List<TimedItem<T>> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        for (TimedItem<T> timed : remaining) {
            dispatch(timed);
        }
    }

    // ─── Metrics ──────────────────────────────────────────────────────────────

    public QueueStats getStats() {
        return new QueueStats(
                name,
                queue != null ? queue.size() : 0,
                capacity,
                workerThreads,
                ttlMillis,
                totalEnqueued.get(),
                totalProcessed.get(),
                totalExpired.get(),
                totalDropped.get(),
                totalErrors.get(),
                running
        );
    }

    public String getName() {
        return name;
    }

    public boolean isRunning() {
        return running;
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    /** Wrapper item + thời điểm enqueue (để tính TTL). */
    private record TimedItem<T>(T item, long enqueueTime) {}

    /** Một message bị chuyển sang Dead Letter Queue. */
    public record DeadLetter<T>(
            T payload,
            String sourceQueue,
            String reason,
            long ageMillis,
            long deadLetterTime,
            Throwable cause
    ) {}

    /** Snapshot metrics của queue. */
    public record QueueStats(
            String name,
            int currentSize,
            int capacity,
            int workerThreads,
            long ttlMillis,
            long enqueued,
            long processed,
            long expired,
            long dropped,
            long errors,
            boolean running
    ) {
        public double usagePercent() {
            return capacity == 0 ? 0 : (currentSize * 100.0 / capacity);
        }
    }
}
