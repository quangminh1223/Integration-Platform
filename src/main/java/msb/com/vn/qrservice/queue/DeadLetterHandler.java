package msb.com.vn.qrservice.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Xử lý chung cho Dead Letter — message hết hạn TTL (3 phút) hoặc xử lý lỗi.
 *
 * Ghi dead letter ra file riêng (dead-letter.log) để:
 * - Audit / điều tra message bị bỏ
 * - Replay sau khi backend phục hồi
 *
 * Dùng làm deadLetterConsumer khi đăng ký queue:
 * <pre>
 * queueManager.register("my-queue", 10000, 2, 100, ttl,
 *     item -&gt; process(item),
 *     deadLetterHandler::handle);   // ← truyền vào đây
 * </pre>
 */
@Slf4j
@Component
public class DeadLetterHandler {

    private static final org.slf4j.Logger DLQ_LOG =
            org.slf4j.LoggerFactory.getLogger("DEAD_LETTER_LOG");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final AtomicLong totalDeadLetters = new AtomicLong(0);

    /**
     * Xử lý một dead letter — ghi ra file dead-letter.log.
     */
    public <T> void handle(ManagedQueue.DeadLetter<T> deadLetter) {
        long count = totalDeadLetters.incrementAndGet();

        Map<String, Object> dlqEntry = new LinkedHashMap<>();
        dlqEntry.put("@timestamp", LocalDateTime.now().format(TS_FMT));
        dlqEntry.put("level", "WARN");
        dlqEntry.put("type", "DEAD_LETTER");
        dlqEntry.put("sourceQueue", deadLetter.sourceQueue());
        dlqEntry.put("reason", deadLetter.reason());
        dlqEntry.put("ageMillis", deadLetter.ageMillis());
        dlqEntry.put("payload", toStringPayload(deadLetter.payload()));

        if (deadLetter.cause() != null) {
            dlqEntry.put("causeClass", deadLetter.cause().getClass().getName());
            dlqEntry.put("causeMessage", deadLetter.cause().getMessage());
        }

        try {
            DLQ_LOG.warn(MAPPER.writeValueAsString(dlqEntry));
        } catch (Exception e) {
            DLQ_LOG.warn(dlqEntry.toString());
        }

        if (count % 50 == 1) {
            log.warn("DeadLetterHandler: đã có {} dead letters từ queue [{}]",
                    count, deadLetter.sourceQueue());
        }
    }

    public long getTotalDeadLetters() {
        return totalDeadLetters.get();
    }

    private String toStringPayload(Object payload) {
        if (payload == null) return "";
        if (payload instanceof String s) return s;
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }
}
