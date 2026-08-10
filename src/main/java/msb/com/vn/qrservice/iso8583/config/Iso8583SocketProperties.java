package msb.com.vn.qrservice.iso8583.config;

import lombok.Data;
import msb.com.vn.qrservice.iso8583.codec.LengthHeaderType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình socket ISO8583 — KHÔNG hard-code, toàn bộ lấy từ application.yml.
 *
 * <pre>
 * iso8583:
 *   socket:
 *     inbound:  { enabled: true, port: 1111 }   ← ServerSocket, nhận bản tin từ đối tác
 *     outbound: { enabled: true, host: localhost, port: 1112 } ← Client, gửi bản tin ra ngoài
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "iso8583.socket")
public class Iso8583SocketProperties {

    /** Đường dẫn file packager (định nghĩa field ISO8583) trên classpath */
    private String packagerConfig = "iso8583/iso8583.xml";

    private Inbound inbound = new Inbound();
    private Outbound outbound = new Outbound();
    private Framing framing = new Framing();
    private Audit audit = new Audit();
    private Transform transform = new Transform();
    private Idempotency idempotency = new Idempotency();
    private Pending pending = new Pending();

    /**
     * Chống hạch toán hai lần. BẮT BUỘC bật vì CORE không hỗ trợ bản tin đảo 0400 —
     * bên gọi gửi lại cùng STAN sau khi nhận RC 68 sẽ khiến CORE hạch toán lần thứ hai.
     */
    @Data
    public static class Idempotency {
        private boolean enabled = true;
        /** Tiền tố khóa trên Redis */
        private String keyPrefix = "iso:idem:";
        /** Chỉ áp dụng cho các MTI này (giao dịch tài chính) */
        private List<String> appliedMtis = new ArrayList<>(List.of("0100", "0200", "0400"));
        /** Thời gian giữ khóa cho giao dịch đã hoàn tất (giây). 86400 = 1 ngày giao dịch */
        private long retentionSeconds = 86400;
        /**
         * Thời gian giữ khóa cho giao dịch timeout, trạng thái UNKNOWN (giây).
         * Giữ lâu hơn vì CORE có thể đã hạch toán — phải chặn gửi lại cho đến khi đối soát xong.
         */
        private long unknownRetentionSeconds = 259200;   // 3 ngày
        /** Response code trả cho bản tin trùng lặp (94 = Duplicate transmission) */
        private String duplicateResponseCode = "94";
    }

    /**
     * Theo dõi giao dịch timeout để đối soát với CORE.
     * Cần thiết vì CORE không đảo được: trả RC 68 nhưng CORE có thể đã hạch toán.
     */
    @Data
    public static class Pending {
        private boolean enabled = true;
        private int queueCapacity = 10000;
        private int writerThreads = 2;
        private int batchSize = 50;
        private long ttlMillis = 300000;
        /** Response code của CORE được coi là ĐÃ hạch toán thành công */
        private List<String> approvedResponseCodes = new ArrayList<>(List.of("00", "10", "11"));
        /** Quá thời gian này mà CORE không trả lời → chuyển sang NEEDS_MANUAL (phút) */
        private long manualEscalationMinutes = 60;
        /** Chu kỳ chạy job đối soát (ms) */
        private long reconcileIntervalMs = 300000;
        /** Số bản ghi xử lý mỗi lượt job */
        private int reconcileBatchSize = 200;
    }

    /** Luồng INBOUND — hệ thống này mở port, hệ thống ngoài kết nối vào để gửi bản tin */
    @Data
    public static class Inbound {
        private boolean enabled = true;
        /** Port lắng nghe bản tin đến */
        private int port = 1111;
        /** Địa chỉ bind — 0.0.0.0 để nhận mọi interface */
        private String bindAddress = "0.0.0.0";
        /** Số connection chờ trong backlog của ServerSocket */
        private int backlog = 128;
        /** Timeout đọc trên mỗi connection (ms). 0 = không timeout */
        private int soTimeoutMs = 0;
        /** Số connection đồng thời tối đa */
        private int maxConnections = 200;
        /** Bật TCP_NODELAY (tắt Nagle) — giảm latency */
        private boolean tcpNoDelay = true;
        /** Bật SO_KEEPALIVE */
        private boolean keepAlive = true;

        /**
         * Trần số bản tin đang xử lý đồng thời (toàn server, không phải mỗi connection).
         *
         * <p>Định luật Little: concurrency = TPS mục tiêu × timeout (giây).
         * Ví dụ 400 TPS × 15s = 6000. Vượt trần thì trả ngay
         * {@link #backpressureResponseCode} thay vì xếp hàng vô hạn.</p>
         */
        private int maxConcurrentMessages = 6000;

        /**
         * Deadline xử lý một bản tin (ms). Quá hạn, watchdog trả response
         * {@link #timeoutResponseCode} để bên gọi không bị treo.
         */
        private long processingTimeoutMs = 15000;

        /** Response code khi quá tải (96 = System malfunction) */
        private String backpressureResponseCode = "96";

        /** Response code khi quá deadline (68 = Response received too late) */
        private String timeoutResponseCode = "68";

        /** Thời gian chờ các bản tin đang xử lý hoàn tất khi đóng connection (ms) */
        private long drainTimeoutMs = 20000;

        /**
         * Số thread cho watchdog cưỡng chế deadline.
         * Watchdog chỉ hẹn giờ và đẩy việc ghi socket sang virtual thread,
         * nên 4 thread đủ cho hàng nghìn bản tin đồng thời.
         */
        private int deadlineSchedulerThreads = 4;

        /**
         * Thời gian tối đa chờ giành quyền ghi socket của một connection (ms).
         *
         * <p>Bên gọi ngừng đọc socket → lệnh ghi block vĩnh viễn và giữ slot xử lý.
         * Quá hạn này thì đóng connection: vừa giải phóng slot, vừa phá vỡ lệnh ghi
         * đang bị treo của thread khác trên cùng connection.</p>
         */
        private long writeAcquireTimeoutMs = 3000;

        /** Bật pipelining: một connection xử lý song song nhiều bản tin, correlate bằng STAN */
        private boolean pipeliningEnabled = true;
    }

    /** Luồng OUTBOUND — hệ thống này là client, kết nối ra đối tác */
    @Data
    public static class Outbound {
        private boolean enabled = true;
        /** Host đích */
        private String host = "localhost";
        /** Port đích */
        private int port = 1112;
        /** Timeout thiết lập kết nối (ms) */
        private int connectTimeoutMs = 5000;
        /** Timeout chờ response cho mỗi bản tin (ms) */
        private long responseTimeoutMs = 15000;
        /** Trần số bản tin outbound đang chờ response đồng thời */
        private int maxPendingRequests = 6000;

        /**
         * Thời gian tối đa chờ giành quyền ghi socket (ms).
         *
         * <p>Bắt buộc phải có: nếu đối tác ngừng đọc socket, TCP receive window đầy và
         * lệnh ghi sẽ block vĩnh viễn (Java blocking I/O KHÔNG có write timeout —
         * SO_TIMEOUT chỉ áp cho read). Không có giới hạn này thì một giao dịch treo
         * sẽ giữ write lock mãi và chặn toàn bộ giao dịch sau.</p>
         */
        private long writeAcquireTimeoutMs = 3000;

        /**
         * Một lệnh ghi kéo dài quá thời gian này (ms) được coi là treo → đóng socket
         * cưỡng chế để giải phóng thread đang bị block, rồi tái kết nối.
         */
        private long writeStallTimeoutMs = 10000;

        /** Chu kỳ watchdog kiểm tra lệnh ghi có bị treo (ms) */
        private long writeStallCheckIntervalMs = 1000;
        /** Timeout đọc socket (ms). 0 = block vô hạn, reader thread tự xử lý */
        private int soTimeoutMs = 0;
        private boolean tcpNoDelay = true;
        private boolean keepAlive = true;
        /** Chu kỳ kiểm tra và tái kết nối (ms) */
        private long reconnectIntervalMs = 10000;
        /** Bật gửi echo test 0800 định kỳ để giữ kết nối */
        private boolean echoEnabled = false;
        /** Chu kỳ gửi echo 0800 (ms) */
        private long echoIntervalMs = 60000;
    }

    /**
     * Audit log giao dịch qua ManagedQueue — bounded, có TTL và Dead Letter Queue.
     * Nằm NGOÀI đường trả response: queue đầy thì drop bản ghi log, giao dịch vẫn chạy.
     */
    @Data
    public static class Audit {
        private boolean enabled = true;
        /** Sức chứa queue. 20000 ở 400 TPS ≈ 50 giây đệm */
        private int queueCapacity = 20000;
        /** Số thread ghi log (tách khỏi thread xử lý giao dịch) */
        private int workerThreads = 2;
        /** Số bản ghi drain mỗi lần poll */
        private int batchSize = 200;
        /** Bản ghi nằm trong queue quá lâu → chuyển sang Dead Letter Queue */
        private long ttlMillis = 180000;
        /**
         * Field cần che trước khi ghi log (PCI DSS).
         * 2=PAN, 35/36/45=track data, 52=PIN block, 55=ICC data.
         */
        private List<Integer> maskFields = new ArrayList<>(List.of(2, 35, 36, 45, 52, 55));
        /** Ghi kèm bản tin gốc dạng hex — chỉ bật khi debug, làm log phình rất nhanh */
        private boolean logRawHex = false;
    }

    /** Bước chuyển đổi ISO8583 ↔ JSON */
    @Data
    public static class Transform {
        /** Bật convert ISO sang JSON trước khi vào business flow */
        private boolean isoToJsonEnabled = true;
        /** Kèm tên đầy đủ của field trong JSON — hữu ích khi debug, tốn dung lượng */
        private boolean includeFieldNames = false;
    }

    /** Cách đóng khung bản tin trên TCP stream */
    @Data
    public static class Framing {
        /** Kiểu length header: BINARY_2 | BINARY_4 | ASCII_4 | BCD_2 | NONE */
        private LengthHeaderType headerType = LengthHeaderType.BINARY_2;
        /** true = giá trị length bao gồm cả số byte của header */
        private boolean lengthIncludesHeader = false;
        /** Độ dài payload tối đa cho phép (byte) — chống OOM khi nhận rác */
        private int maxMessageLength = 8192;
    }
}
