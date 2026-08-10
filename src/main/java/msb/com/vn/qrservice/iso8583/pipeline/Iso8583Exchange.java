package msb.com.vn.qrservice.iso8583.pipeline;

import lombok.Data;
import org.jpos.iso.ISOMsg;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Context mang theo toàn bộ dữ liệu của một bản tin khi đi qua pipeline inbound.
 *
 * <pre>
 * nhận bản tin → AUDIT (queue) → convert ISO→JSON → business flow → response
 *                                      │
 *                          jsonRequest điền tại đây
 * </pre>
 *
 * <p>Mỗi bản tin có một Exchange riêng, chỉ một worker thread chạm vào,
 * nên không cần đồng bộ hoá.</p>
 */
@Data
public class Iso8583Exchange {

    /** ID truy vết xuyên suốt pipeline, có trong mọi dòng log */
    private final String correlationId;

    /** Địa chỉ hệ thống gửi bản tin đến */
    private final String remoteAddress;

    /** Thời điểm nhận được bản tin */
    private final Instant receivedAt;

    /** Bản tin gốc dạng byte trên socket */
    private final byte[] rawRequest;

    /** Bản tin sau khi unpack bằng jPOS */
    private final ISOMsg isoRequest;

    /** MTI của bản tin đến */
    private final String mti;

    /** STAN (field 11) — khóa correlate request/response */
    private final String stan;

    /**
     * Bản tin đã convert sang JSON. Điền bởi bước transformation,
     * business flow đọc từ đây thay vì đọc trực tiếp ISOMsg.
     */
    private Map<String, Object> jsonRequest;

    /** Bản tin response do business flow dựng ra */
    private ISOMsg isoResponse;

    /** Response code (field 39) của response */
    private String responseCode;

    /** Thời điểm hoàn tất xử lý */
    private Instant completedAt;

    /** Thông điệp lỗi nếu xử lý thất bại */
    private String errorMessage;

    /** Biến phụ trợ dùng chung giữa các bước trong pipeline */
    private final Map<String, Object> attributes = new HashMap<>();

    public Iso8583Exchange(String correlationId,
                           String remoteAddress,
                           Instant receivedAt,
                           byte[] rawRequest,
                           ISOMsg isoRequest,
                           String mti,
                           String stan) {
        this.correlationId = correlationId;
        this.remoteAddress = remoteAddress;
        this.receivedAt = receivedAt;
        this.rawRequest = rawRequest;
        this.isoRequest = isoRequest;
        this.mti = mti;
        this.stan = stan;
    }

    public void putAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /** Thời gian xử lý tính đến hiện tại (ms) */
    public long elapsedMs() {
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.toEpochMilli() - receivedAt.toEpochMilli();
    }
}
