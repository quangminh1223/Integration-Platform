package msb.com.vn.qrservice.iso8583.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Trạng thái hai kênh socket ISO8583.
 */
@Data
@Builder
@Schema(description = "Trạng thái socket ISO8583 inbound và outbound")
public class Iso8583SocketStatus {

    private InboundStatus inbound;
    private OutboundStatus outbound;

    @Data
    @Builder
    @Schema(description = "Trạng thái kênh nhận bản tin")
    public static class InboundStatus {
        private boolean enabled;
        private boolean listening;
        private int port;
        private int activeConnections;

        @Schema(description = "Số bản tin đang xử lý")
        private int inFlightMessages;
        @Schema(description = "Số slot xử lý còn trống. 0 = đang backpressure")
        private int availablePermits;
        @Schema(description = "Trần bản tin xử lý đồng thời")
        private int maxConcurrentMessages;
        @Schema(description = "Deadline xử lý một bản tin (ms)")
        private long processingTimeoutMs;

        private long totalReceived;
        private long totalFailed;
        @Schema(description = "Số bản tin bị từ chối do quá tải")
        private long totalRejected;
        @Schema(description = "Số bản tin vượt deadline xử lý")
        private long totalTimedOut;
    }

    @Data
    @Builder
    @Schema(description = "Trạng thái kênh gửi bản tin")
    public static class OutboundStatus {
        private boolean enabled;
        private boolean connected;
        private String target;
        private int pendingRequests;
        @Schema(description = "Trần request chờ response đồng thời")
        private int maxPendingRequests;
        @Schema(description = "Timeout chờ response (ms)")
        private long responseTimeoutMs;
        private long totalSent;
        private long totalReceived;
        private long totalTimeout;
    }
}
