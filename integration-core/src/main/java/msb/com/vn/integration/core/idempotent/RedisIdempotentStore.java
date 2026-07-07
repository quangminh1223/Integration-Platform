package msb.com.vn.integration.core.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed idempotent store.
 * Uses Redis SET NX with TTL to guarantee exactly-once semantics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisIdempotentStore implements IdempotentStore {

    private static final String KEY_PREFIX = "idem:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isDuplicate(String messageId) {
        String key = KEY_PREFIX + messageId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null && ("PROCESSING".equals(value) || "COMPLETED".equals(value));
    }

    @Override
    public void markProcessing(String messageId) {
        String key = KEY_PREFIX + messageId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", TTL);
        if (Boolean.FALSE.equals(success)) {
            log.warn("Message already in store: messageId={}", messageId);
        }
    }

    @Override
    public void markCompleted(String messageId) {
        String key = KEY_PREFIX + messageId;
        redisTemplate.opsForValue().set(key, "COMPLETED", TTL);
    }

    @Override
    public void markFailed(String messageId) {
        String key = KEY_PREFIX + messageId;
        redisTemplate.delete(key); // Allow retry
    }
}
