package msb.com.vn.dsign.hsm;

import msb.com.vn.dsign.config.HsmConfig;
import msb.com.vn.dsign.exception.HsmUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HsmConnectionPool verifying pool management,
 * session borrowing/returning, and queue depth enforcement.
 */
class HsmConnectionPoolTest {

    private HsmConfig hsmConfig;

    @BeforeEach
    void setUp() {
        hsmConfig = new HsmConfig();
        hsmConfig.setPoolSize(3);
        hsmConfig.setConnectionTimeoutMs(500);
        hsmConfig.setIdleTimeoutMs(60000);
        hsmConfig.setMaxQueueDepth(2);

        HsmConfig.SlotConfig slot0 = new HsmConfig.SlotConfig();
        slot0.setId("slot-0");
        slot0.setPin("1234");
        hsmConfig.setSlots(List.of(slot0));
    }

    @Test
    void init_shouldPreCreateSessionsUpToPoolSize() {
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        assertEquals(3, pool.getAvailableCount());
        assertEquals(0, pool.getQueuedCount());
    }

    @Test
    void borrowSession_shouldReturnSessionWhenAvailable() {
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        HsmSession session = pool.borrowSession();

        assertNotNull(session);
        assertEquals("slot-0", session.getSlotId());
        assertTrue(session.getCreatedAt() > 0);
        assertNotNull(session.getNativeSession());
        assertEquals(2, pool.getAvailableCount());
    }

    @Test
    void borrowSession_shouldDrainPoolCompletely() {
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        List<HsmSession> borrowed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            borrowed.add(pool.borrowSession());
        }

        assertEquals(3, borrowed.size());
        assertEquals(0, pool.getAvailableCount());
        borrowed.forEach(s -> assertNotNull(s.getSlotId()));
    }

    @Test
    void borrowSession_shouldThrowWhenQueueDepthExceeded() throws Exception {
        hsmConfig.setPoolSize(1);
        hsmConfig.setMaxQueueDepth(1);
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        // Borrow the only session
        HsmSession session = pool.borrowSession();
        assertNotNull(session);

        // First queued request will wait (queue depth = 1 allowed)
        // Second request should fail because queue depth is already at max
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstWaiting = new CountDownLatch(1);
        AtomicInteger exceptionsThrown = new AtomicInteger(0);

        // Thread 1: will queue and wait (brings queue depth to 1)
        Future<?> waitingThread = executor.submit(() -> {
            firstWaiting.countDown();
            try {
                pool.borrowSession(); // will block until timeout
            } catch (HsmUnavailableException e) {
                // Expected - timeout
                exceptionsThrown.incrementAndGet();
            }
        });

        // Wait for thread 1 to start waiting
        firstWaiting.await();
        Thread.sleep(50); // Give time for thread to enter poll wait

        // Thread 2: should be rejected immediately because queue is at max (1)
        Future<?> rejectedThread = executor.submit(() -> {
            try {
                pool.borrowSession();
                fail("Should have thrown HsmUnavailableException");
            } catch (HsmUnavailableException e) {
                assertTrue(e.getMessage().contains("queue depth"));
                exceptionsThrown.incrementAndGet();
            }
        });

        rejectedThread.get();
        waitingThread.get();

        assertEquals(2, exceptionsThrown.get());
        executor.shutdown();
    }

    @Test
    void borrowSession_shouldTimeoutWhenPoolExhausted() throws Exception {
        hsmConfig.setPoolSize(1);
        hsmConfig.setConnectionTimeoutMs(100);
        hsmConfig.setMaxQueueDepth(5);
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        // Borrow the only session
        pool.borrowSession();

        // Next borrow should timeout
        long start = System.currentTimeMillis();
        HsmUnavailableException ex = assertThrows(HsmUnavailableException.class, pool::borrowSession);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(ex.getMessage().contains("Timed out"));
        assertTrue(elapsed >= 100, "Should have waited at least connectionTimeoutMs");
    }

    @Test
    void returnSession_shouldMakeSessionAvailableAgain() {
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        HsmSession session = pool.borrowSession();
        assertEquals(2, pool.getAvailableCount());

        pool.returnSession(session);
        assertEquals(3, pool.getAvailableCount());
    }

    @Test
    void returnSession_shouldHandleNullGracefully() {
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        // Should not throw
        pool.returnSession(null);
        assertEquals(3, pool.getAvailableCount());
    }

    @Test
    void borrowAndReturn_shouldAllowReuse() {
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        // Borrow all sessions
        List<HsmSession> sessions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sessions.add(pool.borrowSession());
        }
        assertEquals(0, pool.getAvailableCount());

        // Return them all
        sessions.forEach(pool::returnSession);
        assertEquals(3, pool.getAvailableCount());

        // Borrow again — should work
        HsmSession reusedSession = pool.borrowSession();
        assertNotNull(reusedSession);
    }

    @Test
    void destroy_shouldClearAllSessions() {
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();
        assertEquals(3, pool.getAvailableCount());

        pool.destroy();
        assertEquals(0, pool.getAvailableCount());
    }

    @Test
    void concurrentBorrowReturn_shouldNotLoseSessions() throws Exception {
        hsmConfig.setPoolSize(5);
        hsmConfig.setMaxQueueDepth(50);
        hsmConfig.setConnectionTimeoutMs(2000);
        HsmConnectionPool pool = new HsmConnectionPool(hsmConfig);
        pool.init();

        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(iterations);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                try {
                    HsmSession session = pool.borrowSession();
                    Thread.sleep(5); // Simulate short work
                    pool.returnSession(session);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // May happen under extreme contention
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // All sessions should be returned
        assertEquals(5, pool.getAvailableCount());
        assertTrue(successCount.get() > 0, "At least some operations should succeed");
    }
}
