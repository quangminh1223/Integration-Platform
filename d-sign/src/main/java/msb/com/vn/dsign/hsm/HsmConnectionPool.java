package msb.com.vn.dsign.hsm;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.dsign.config.HsmConfig;
import msb.com.vn.dsign.exception.HsmUnavailableException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection pool managing PKCS#11 session objects for HSM communication.
 *
 * <p>Sessions are pre-created at startup and recycled after use.
 * When the pool is exhausted, requests queue up to a configurable maximum
 * queue depth. Requests beyond that limit are immediately rejected with
 * {@link HsmUnavailableException} (HTTP 503).</p>
 */
@Slf4j
public class HsmConnectionPool {

    private final HsmConfig hsmConfig;
    private final ArrayBlockingQueue<HsmSession> availableSessions;
    private final AtomicInteger queuedCount;

    public HsmConnectionPool(HsmConfig hsmConfig) {
        this.hsmConfig = hsmConfig;
        this.availableSessions = new ArrayBlockingQueue<>(hsmConfig.getPoolSize());
        this.queuedCount = new AtomicInteger(0);
    }

    /**
     * Initialize the pool by pre-creating sessions up to the configured pool size.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing HSM connection pool with size={}", hsmConfig.getPoolSize());
        for (int i = 0; i < hsmConfig.getPoolSize(); i++) {
            HsmSession session = createSession(i);
            availableSessions.offer(session);
        }
        log.info("HSM connection pool initialized with {} sessions", availableSessions.size());
    }

    /**
     * Borrow a session from the pool.
     *
     * <p>If the pool is empty:
     * <ul>
     *   <li>If the current queue depth is below {@code maxQueueDepth}, the caller waits
     *       up to {@code connectionTimeoutMs} for a session to become available.</li>
     *   <li>If the current queue depth has reached {@code maxQueueDepth}, an
     *       {@link HsmUnavailableException} is thrown immediately without blocking.</li>
     * </ul>
     *
     * @return a borrowed HSM session
     * @throws HsmUnavailableException if the queue is full or timeout is exceeded
     */
    public HsmSession borrowSession() {
        // Fast path: try non-blocking poll first
        HsmSession session = availableSessions.poll();
        if (session != null) {
            return session;
        }

        // Pool exhausted — check queue depth before blocking
        int currentQueued = queuedCount.get();
        if (currentQueued >= hsmConfig.getMaxQueueDepth()) {
            log.warn("HSM connection pool exhausted and queue depth reached maximum ({}). Rejecting request.",
                    hsmConfig.getMaxQueueDepth());
            throw new HsmUnavailableException(
                    "HSM connection pool exhausted: queue depth " + currentQueued
                            + " has reached maximum " + hsmConfig.getMaxQueueDepth());
        }

        // Increment queued count and wait for a session
        queuedCount.incrementAndGet();
        try {
            session = availableSessions.poll(hsmConfig.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS);
            if (session == null) {
                log.warn("Timed out waiting for HSM session after {}ms", hsmConfig.getConnectionTimeoutMs());
                throw new HsmUnavailableException(
                        "Timed out waiting for HSM session after " + hsmConfig.getConnectionTimeoutMs() + "ms");
            }
            return session;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HsmUnavailableException("Interrupted while waiting for HSM session", e);
        } finally {
            queuedCount.decrementAndGet();
        }
    }

    /**
     * Return a session to the pool for reuse.
     *
     * @param session the session to return
     */
    public void returnSession(HsmSession session) {
        if (session == null) {
            return;
        }
        boolean offered = availableSessions.offer(session);
        if (!offered) {
            log.warn("Could not return session to pool (pool full). Session for slot {} discarded.",
                    session.getSlotId());
        }
    }

    /**
     * Shut down the pool and clean up all sessions.
     */
    @PreDestroy
    public void destroy() {
        log.info("Destroying HSM connection pool. Clearing {} sessions.", availableSessions.size());
        availableSessions.clear();
    }

    /**
     * Get the count of currently available (idle) sessions in the pool.
     *
     * @return number of available sessions
     */
    public int getAvailableCount() {
        return availableSessions.size();
    }

    /**
     * Get the count of requests currently queued waiting for a session.
     *
     * @return number of queued requests
     */
    public int getQueuedCount() {
        return queuedCount.get();
    }

    private HsmSession createSession(int index) {
        String slotId = resolveSlotId(index);
        return HsmSession.builder()
                .slotId(slotId)
                .createdAt(System.currentTimeMillis())
                .nativeSession(new Object()) // Placeholder for real PKCS#11 session
                .build();
    }

    private String resolveSlotId(int index) {
        if (hsmConfig.getSlots() != null && !hsmConfig.getSlots().isEmpty()) {
            int slotIndex = index % hsmConfig.getSlots().size();
            return hsmConfig.getSlots().get(slotIndex).getId();
        }
        return "slot-" + index;
    }
}
