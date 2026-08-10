package msb.com.vn.qrservice.iso8583.idempotent;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Chống hạch toán hai lần cho giao dịch tài chính ISO8583.
 *
 * <p><b>Vì sao bắt buộc:</b> CORE không hỗ trợ bản tin đảo 0400. Khi ta trả RC 68 do timeout,
 * bên gọi sẽ gửi lại cùng STAN. Không có lớp này thì CORE hạch toán lần thứ hai và khách bị
 * trừ tiền hai lần.</p>
 *
 * <h3>Khóa định danh</h3>
 * <p>Theo thông lệ ISO8583, STAN (field 11) là duy nhất theo terminal trong một ngày.
 * Khóa = {@code STAN : terminalId : localTxnDate}.</p>
 *
 * <h3>Trạng thái</h3>
 * <pre>
 * (chưa có)   → tryBegin thành công → IN_PROGRESS → markCompleted → COMPLETED (kèm response)
 *                                                 → markUnknown   → UNKNOWN (timeout, CORE bất định)
 *                                                 → release       → xoá khoá, cho phép gửi lại
 * </pre>
 *
 * <p>Gặp lại khóa đã tồn tại:</p>
 * <ul>
 *   <li>COMPLETED → phát lại đúng response đã trả, không gọi CORE</li>
 *   <li>IN_PROGRESS → bản gốc đang chạy, trả RC trùng lặp</li>
 *   <li>UNKNOWN → bản gốc đã timeout và CORE có thể đã hạch toán, TUYỆT ĐỐI không gọi lại</li>
 * </ul>
 */
@Slf4j
@Component
public class IsoIdempotencyStore {

    private static final String STATE_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_UNKNOWN = "UNKNOWN";

    private static final String FIELD_SEPARATOR = "|";

    private final StringRedisTemplate redisTemplate;
    private final Iso8583SocketProperties properties;

    public IsoIdempotencyStore(StringRedisTemplate redisTemplate,
                               Iso8583SocketProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Sinh khóa định danh giao dịch. Trả về {@link Optional#empty()} nếu bản tin
     * thiếu dữ liệu để định danh — khi đó không thể áp dụng idempotency.
     */
    public Optional<String> buildKey(Iso8583Exchange exchange) {
        ISOMsg msg = exchange.getIsoRequest();
        String stan = exchange.getStan();
        if (stan == null || stan.isBlank()) {
            return Optional.empty();
        }

        String terminalId = msg.hasField(41) ? msg.getString(41) : "NOTERM";
        String localDate = msg.hasField(IsoMessageUtils.FIELD_LOCAL_DATE)
                ? msg.getString(IsoMessageUtils.FIELD_LOCAL_DATE)
                : "NODATE";

        return Optional.of(properties.getIdempotency().getKeyPrefix()
                + stan + ":" + terminalId + ":" + localDate);
    }

    /**
     * Cố gắng chiếm quyền xử lý giao dịch.
     *
     * @return kết quả cho biết được phép xử lý, hay là bản trùng lặp (kèm trạng thái bản gốc)
     */
    public IdempotencyCheck tryBegin(String key) {
        Duration ttl = Duration.ofSeconds(properties.getIdempotency().getRetentionSeconds());

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, STATE_IN_PROGRESS, ttl);

        if (Boolean.TRUE.equals(acquired)) {
            return IdempotencyCheck.builder().duplicate(false).build();
        }

        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            // Khóa vừa hết hạn giữa hai lệnh — thử chiếm lại một lần
            Boolean retry = redisTemplate.opsForValue().setIfAbsent(key, STATE_IN_PROGRESS, ttl);
            if (Boolean.TRUE.equals(retry)) {
                return IdempotencyCheck.builder().duplicate(false).build();
            }
            stored = redisTemplate.opsForValue().get(key);
        }

        return parseStored(key, stored);
    }

    /**
     * Ghi nhận đã xử lý xong, lưu kèm response để phát lại cho bản trùng lặp.
     */
    public void markCompleted(String key, String responseCode, String responseHex) {
        String value = STATE_COMPLETED + FIELD_SEPARATOR
                + nullToEmpty(responseCode) + FIELD_SEPARATOR
                + nullToEmpty(responseHex);
        redisTemplate.opsForValue().set(key, value,
                Duration.ofSeconds(properties.getIdempotency().getRetentionSeconds()));
    }

    /**
     * Ghi nhận giao dịch timeout — CORE có thể đã hạch toán hoặc chưa.
     *
     * <p>KHÔNG được xoá khóa ở trạng thái này: xoá đi thì bên gọi gửi lại sẽ chạm CORE
     * lần thứ hai và có nguy cơ hạch toán trùng.</p>
     */
    public void markUnknown(String key, String responseCode) {
        String value = STATE_UNKNOWN + FIELD_SEPARATOR + nullToEmpty(responseCode) + FIELD_SEPARATOR;
        redisTemplate.opsForValue().set(key, value,
                Duration.ofSeconds(properties.getIdempotency().getUnknownRetentionSeconds()));
    }

    /**
     * Giải phóng khóa — chỉ dùng khi CHẮC CHẮN chưa chạm tới CORE
     * (ví dụ trượt validation, không tìm được handler), để bên gọi được phép gửi lại.
     */
    public void release(String key) {
        redisTemplate.delete(key);
    }

    // ─── Nội bộ ─────────────────────────────────────────────────────────────

    private IdempotencyCheck parseStored(String key, String stored) {
        if (stored == null) {
            log.warn("Không đọc được trạng thái idempotency của khóa {} — cho phép xử lý", key);
            return IdempotencyCheck.builder().duplicate(false).build();
        }

        String[] parts = stored.split("\\" + FIELD_SEPARATOR, -1);
        String state = parts[0];

        return switch (state) {
            case STATE_COMPLETED -> IdempotencyCheck.builder()
                    .duplicate(true)
                    .state(STATE_COMPLETED)
                    .storedResponseCode(emptyToNull(parts.length > 1 ? parts[1] : null))
                    .storedResponseHex(emptyToNull(parts.length > 2 ? parts[2] : null))
                    .build();

            case STATE_UNKNOWN -> IdempotencyCheck.builder()
                    .duplicate(true)
                    .state(STATE_UNKNOWN)
                    .storedResponseCode(emptyToNull(parts.length > 1 ? parts[1] : null))
                    .build();

            default -> IdempotencyCheck.builder()
                    .duplicate(true)
                    .state(STATE_IN_PROGRESS)
                    .build();
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * Kết quả kiểm tra idempotency.
     */
    @Getter
    @Builder
    public static class IdempotencyCheck {
        /** true = bản tin này trùng với một giao dịch đã/đang xử lý */
        private final boolean duplicate;
        /** Trạng thái bản gốc: COMPLETED | IN_PROGRESS | UNKNOWN */
        private final String state;
        /** Response code đã trả cho bản gốc */
        private final String storedResponseCode;
        /** Response đã trả cho bản gốc, dạng hex — phát lại nguyên vẹn */
        private final String storedResponseHex;

        public boolean isCompleted() {
            return STATE_COMPLETED.equals(state);
        }

        public boolean isUnknown() {
            return STATE_UNKNOWN.equals(state);
        }

        public boolean isInProgress() {
            return STATE_IN_PROGRESS.equals(state);
        }
    }
}
