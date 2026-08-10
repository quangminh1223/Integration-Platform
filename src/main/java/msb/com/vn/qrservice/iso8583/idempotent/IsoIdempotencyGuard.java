package msb.com.vn.qrservice.iso8583.idempotent;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.config.Iso8583PackagerProvider;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.inbound.Iso8583HandlerDispatcher;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cổng chống hạch toán hai lần, đặt trước bước gọi CORE.
 *
 * <p><b>Lý do tồn tại:</b> CORE không hỗ trợ bản tin đảo 0400. Sau khi ta trả RC 68 do timeout,
 * bên gọi sẽ gửi lại cùng STAN. Nếu để bản tin đó chạm CORE lần thứ hai, khách bị trừ tiền
 * hai lần và không có cách nào hoàn tác tự động.</p>
 *
 * <p>Chỉ áp dụng cho các MTI giao dịch tài chính khai báo trong
 * {@code iso8583.socket.idempotency.applied-mtis}. Bản tin quản trị mạng (0800) không cần.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IsoIdempotencyGuard {

    private final IsoIdempotencyStore store;
    private final Iso8583PackagerProvider packagerProvider;
    private final Iso8583HandlerDispatcher dispatcher;
    private final Iso8583SocketProperties properties;

    /**
     * Kiểm tra và chiếm quyền xử lý.
     *
     * @return kết quả: được phép xử lý, hay là bản trùng lặp cần trả lại response cũ
     */
    public GuardResult check(Iso8583Exchange exchange) {
        Iso8583SocketProperties.Idempotency cfg = properties.getIdempotency();

        if (!cfg.isEnabled() || !cfg.getAppliedMtis().contains(exchange.getMti())) {
            return GuardResult.allowed(null);
        }

        Optional<String> keyOpt = store.buildKey(exchange);
        if (keyOpt.isEmpty()) {
            log.warn("Bản tin MTI={} thiếu STAN nên không áp dụng được idempotency, correlationId={}",
                    exchange.getMti(), exchange.getCorrelationId());
            return GuardResult.allowed(null);
        }

        String key = keyOpt.get();
        IsoIdempotencyStore.IdempotencyCheck check = store.tryBegin(key);

        if (!check.isDuplicate()) {
            return GuardResult.allowed(key);
        }

        log.warn("BẢN TIN TRÙNG LẶP — stan={}, terminal={}, trạng thái bản gốc={}, correlationId={}",
                exchange.getStan(), fieldOrNull(exchange.getIsoRequest(), 41),
                check.getState(), exchange.getCorrelationId());

        return GuardResult.duplicate(key, check);
    }

    /**
     * Dựng response cho bản tin trùng lặp.
     *
     * <ul>
     *   <li>Bản gốc đã hoàn tất → phát lại đúng response cũ, bên gọi nhận kết quả thật</li>
     *   <li>Bản gốc đang chạy hoặc đã timeout → trả response code trùng lặp,
     *       KHÔNG gọi CORE lần hai</li>
     * </ul>
     */
    public ISOMsg buildDuplicateResponse(Iso8583Exchange exchange, GuardResult result) {
        IsoIdempotencyStore.IdempotencyCheck check = result.getCheck();

        // Bản gốc đã xong và có response lưu lại → phát lại nguyên vẹn
        if (check != null && check.isCompleted() && check.getStoredResponseHex() != null) {
            try {
                ISOMsg replay = packagerProvider.unpack(
                        IsoMessageUtils.hexToBytes(check.getStoredResponseHex()));
                log.info("Phát lại response đã lưu cho bản tin trùng lặp: stan={}, rc={}",
                        exchange.getStan(), check.getStoredResponseCode());
                return replay;
            } catch (Exception e) {
                log.error("Không phát lại được response đã lưu (stan={}): {}",
                        exchange.getStan(), e.getMessage());
            }
        }

        // Bản gốc đang chạy, hoặc đã timeout và CORE có thể đã hạch toán
        String rc = properties.getIdempotency().getDuplicateResponseCode();
        if (check != null && check.isUnknown()) {
            log.error("Bản tin gửi lại cho giao dịch ĐANG TREO (CORE có thể đã hạch toán). "
                            + "Trả RC {} và KHÔNG gọi CORE. stan={}, correlationId={}",
                    rc, exchange.getStan(), exchange.getCorrelationId());
        }
        return dispatcher.buildRejectResponse(
                exchange.getIsoRequest(), rc, exchange.getCorrelationId());
    }

    /**
     * Chốt kết quả xử lý thành công, lưu response để phát lại cho bản gửi lại.
     */
    public void markCompleted(String key, ISOMsg response, String responseCode) {
        if (key == null) {
            return;
        }
        try {
            String hex = IsoMessageUtils.bytesToHex(packagerProvider.pack(response));
            store.markCompleted(key, responseCode, hex);
        } catch (Exception e) {
            log.error("Không lưu được response vào idempotency store (key={}): {}", key, e.getMessage());
            store.markCompleted(key, responseCode, null);
        }
    }

    /**
     * Đánh dấu giao dịch timeout — CORE bất định.
     * Khóa được giữ lâu để chặn bên gọi gửi lại chạm CORE lần hai.
     */
    public void markUnknown(String key, String responseCode) {
        if (key == null) {
            return;
        }
        store.markUnknown(key, responseCode);
    }

    /**
     * Giải phóng khóa — chỉ gọi khi CHẮC CHẮN chưa chạm CORE.
     */
    public void release(String key) {
        if (key == null) {
            return;
        }
        store.release(key);
    }

    private static String fieldOrNull(ISOMsg msg, int field) {
        return msg != null && msg.hasField(field) ? msg.getString(field) : null;
    }

    /**
     * Kết quả kiểm tra idempotency cho một bản tin.
     */
    @Getter
    @Builder
    public static class GuardResult {
        /** Khóa idempotency; null nếu không áp dụng cho bản tin này */
        private final String key;
        private final boolean duplicate;
        private final IsoIdempotencyStore.IdempotencyCheck check;

        static GuardResult allowed(String key) {
            return GuardResult.builder().key(key).duplicate(false).build();
        }

        static GuardResult duplicate(String key, IsoIdempotencyStore.IdempotencyCheck check) {
            return GuardResult.builder().key(key).duplicate(true).check(check).build();
        }

        public String getOriginalState() {
            return check != null ? check.getState() : null;
        }
    }
}
