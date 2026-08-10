package msb.com.vn.dsign.hsm;

import msb.com.vn.dsign.config.HsmConfig;
import msb.com.vn.dsign.exception.HsmUnavailableException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for HSM connection pool exhaustion behavior.
 *
 * <p><b>Validates: Requirements 3.3, 3.5</b></p>
 *
 * <p>Property 15: HSM adapter resilience under pool exhaustion —
 * For any state where the HSM connection pool is fully occupied and the request queue
 * has reached the configured max-queue-depth, the next incoming signing request SHALL
 * receive an HsmUnavailableException (HTTP 503) without blocking indefinitely.</p>
 */
class HsmPoolExhaustionPropertyTest {

    private static final int POOL_SIZE = 2;
    private static final int MAX_QUEUE_DEPTH = 3;
    private static final long CONNECTION_TIMEOUT_MS = 2000;
    private static final long IMMEDIATE_REJECTION_THRESHOLD_MS = 100;

    /**
     * Property 15: HSM adapter resilience under pool exhaustion.
     *
     * <p>When the pool is fully exhausted and the queue is at max depth,
     * any additional requests (1-20 random extra requests) must be rejected
     * immediately with HsmUnavailableException without blocking.</p>
     *
     * <p><b>Validates: Requirements 3.3, 3.5</b></p>
     */
    @Property(tries = 100)
    void poolExhaustedWithMaxQueueDepth_rejectsImmediately(
            @ForAll @IntRange(min = 1, max = 20) int extraRequests) throws Exception {

        // Setup pool with small poolSize=2, maxQueueDepth=3
        HsmConfig config = createConfig();
        HsmConnectionPool pool = new HsmConnectionPool(config);
        pool.init();

        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch queueFilledLatch = new CountDownLatch(MAX_QUEUE_DEPTH);
        List<HsmSession> borrowedSessions = new ArrayList<>();

        try {
            // Step 1: Exhaust the pool by borrowing ALL sessions
            for (int i = 0; i < POOL_SIZE; i++) {
                HsmSession session = pool.borrowSession();
                assertNotNull(session, "Should be able to borrow session " + i);
                borrowedSessions.add(session);
            }
            assertEquals(0, pool.getAvailableCount(),
                    "Pool should be fully exhausted");

            // Step 2: Fill the queue to max depth with waiting threads
            List<Future<?>> waitingFutures = new ArrayList<>();
            for (int i = 0; i < MAX_QUEUE_DEPTH; i++) {
                Future<?> future = executor.submit(() -> {
                    queueFilledLatch.countDown();
                    try {
                        // These threads will block waiting for a session (up to connectionTimeoutMs)
                        pool.borrowSession();
                    } catch (HsmUnavailableException e) {
                        // Expected — timeout waiting for session
                    }
                });
                waitingFutures.add(future);
            }

            // Wait for all queue threads to start and enter the waiting state
            assertTrue(queueFilledLatch.await(2, TimeUnit.SECONDS),
                    "Queue threads should have started");
            // Give threads time to enter the poll wait inside borrowSession
            Thread.sleep(50);

            // Step 3: Generate extra requests that should ALL be rejected immediately
            AtomicInteger rejectedCount = new AtomicInteger(0);
            List<Future<Long>> rejectionFutures = new ArrayList<>();

            for (int i = 0; i < extraRequests; i++) {
                Future<Long> rejectionFuture = executor.submit(() -> {
                    long start = System.nanoTime();
                    try {
                        pool.borrowSession();
                        fail("Should have thrown HsmUnavailableException");
                        return -1L; // unreachable
                    } catch (HsmUnavailableException e) {
                        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                        rejectedCount.incrementAndGet();
                        // Verify exception message indicates queue depth exceeded
                        assertTrue(e.getMessage().contains("queue depth") || e.getMessage().contains("exhausted"),
                                "Exception should indicate pool/queue exhaustion: " + e.getMessage());
                        return elapsedMs;
                    }
                });
                rejectionFutures.add(rejectionFuture);
            }

            // Step 4: Verify all extra requests were rejected immediately (< 100ms)
            for (int i = 0; i < rejectionFutures.size(); i++) {
                long elapsedMs = rejectionFutures.get(i).get(5, TimeUnit.SECONDS);
                assertTrue(elapsedMs < IMMEDIATE_REJECTION_THRESHOLD_MS,
                        "Request " + i + " should be rejected immediately (< " +
                                IMMEDIATE_REJECTION_THRESHOLD_MS + "ms) but took " + elapsedMs + "ms");
            }

            // Verify ALL extra requests were rejected
            assertEquals(extraRequests, rejectedCount.get(),
                    "All " + extraRequests + " extra requests should have been rejected");

            // Cleanup: return sessions so waiting threads can unblock
            borrowedSessions.forEach(pool::returnSession);
            borrowedSessions.clear();

            // Wait for queued threads to complete
            for (Future<?> f : waitingFutures) {
                f.get(5, TimeUnit.SECONDS);
            }

        } finally {
            // Ensure sessions are returned even if test fails
            borrowedSessions.forEach(pool::returnSession);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            pool.destroy();
        }
    }

    private HsmConfig createConfig() {
        HsmConfig config = new HsmConfig();
        config.setPoolSize(POOL_SIZE);
        config.setMaxQueueDepth(MAX_QUEUE_DEPTH);
        config.setConnectionTimeoutMs(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeoutMs(60000);

        HsmConfig.SlotConfig slot = new HsmConfig.SlotConfig();
        slot.setId("test-slot-0");
        slot.setPin("0000");
        config.setSlots(List.of(slot));

        return config;
    }
}
