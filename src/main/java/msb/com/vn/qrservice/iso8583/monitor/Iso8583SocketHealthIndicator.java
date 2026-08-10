package msb.com.vn.qrservice.iso8583.monitor;

import lombok.RequiredArgsConstructor;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.inbound.Iso8583InboundServer;
import msb.com.vn.qrservice.iso8583.outbound.Iso8583OutboundClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check cho hai kênh socket ISO8583, hiển thị tại /actuator/health.
 *
 * <p>Quy tắc: kênh đang TẮT không làm health DOWN. Chỉ báo DOWN khi kênh
 * được bật nhưng không hoạt động.</p>
 */
@Component("iso8583Socket")
@RequiredArgsConstructor
public class Iso8583SocketHealthIndicator implements HealthIndicator {

    private final Iso8583InboundServer inboundServer;
    private final Iso8583OutboundClient outboundClient;
    private final Iso8583SocketProperties properties;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean healthy = true;

        // ── Inbound ──
        boolean inboundEnabled = properties.getInbound().isEnabled();
        boolean inboundUp = inboundServer.isRunning();
        int maxConcurrent = properties.getInbound().getMaxConcurrentMessages();
        int permits = inboundServer.getAvailablePermits();

        Map<String, Object> inboundDetails = new LinkedHashMap<>();
        inboundDetails.put("enabled", inboundEnabled);
        inboundDetails.put("status", inboundEnabled ? (inboundUp ? "UP" : "DOWN") : "DISABLED");
        inboundDetails.put("port", properties.getInbound().getPort());
        inboundDetails.put("activeConnections", inboundServer.getActiveConnections().get());
        inboundDetails.put("inFlightMessages", inboundServer.getInFlightMessages().get());
        inboundDetails.put("availablePermits", permits);
        inboundDetails.put("maxConcurrentMessages", maxConcurrent);
        inboundDetails.put("saturation", maxConcurrent > 0
                ? String.format("%.1f%%", 100.0 * (maxConcurrent - permits) / maxConcurrent) : "n/a");
        inboundDetails.put("totalReceived", inboundServer.getTotalMessagesReceived().get());
        inboundDetails.put("totalFailed", inboundServer.getTotalMessagesFailed().get());
        inboundDetails.put("totalRejected", inboundServer.getTotalMessagesRejected().get());
        inboundDetails.put("totalTimedOut", inboundServer.getTotalMessagesTimedOut().get());
        details.put("inbound", inboundDetails);

        if (inboundEnabled && !inboundUp) {
            healthy = false;
        }
        // Hết slot xử lý = đang từ chối bản tin → báo DOWN để load balancer ngừng đẩy traffic
        if (inboundEnabled && inboundUp && permits == 0) {
            inboundDetails.put("warning", "Hết slot xử lý — đang từ chối bản tin mới");
            healthy = false;
        }

        // ── Outbound ──
        boolean outboundEnabled = properties.getOutbound().isEnabled();
        boolean outboundUp = outboundClient.isConnected();
        details.put("outbound", Map.of(
                "enabled", outboundEnabled,
                "status", outboundEnabled ? (outboundUp ? "UP" : "DOWN") : "DISABLED",
                "target", outboundClient.getTargetAddress(),
                "pendingRequests", outboundClient.getPendingRequestCount(),
                "totalSent", outboundClient.getTotalSent().get(),
                "totalReceived", outboundClient.getTotalReceived().get(),
                "totalTimeout", outboundClient.getTotalTimeout().get()
        ));
        if (outboundEnabled && !outboundUp) {
            healthy = false;
        }

        return healthy
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
