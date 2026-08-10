package msb.com.vn.qrservice.iso8583.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.common.exception.Iso8583Exception;
import msb.com.vn.qrservice.iso8583.config.Iso8583PackagerProvider;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.dto.Iso8583SendRequest;
import msb.com.vn.qrservice.iso8583.dto.Iso8583SendResponse;
import msb.com.vn.qrservice.iso8583.dto.Iso8583SocketStatus;
import msb.com.vn.qrservice.iso8583.inbound.Iso8583InboundServer;
import msb.com.vn.qrservice.iso8583.outbound.Iso8583OutboundClient;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Nghiệp vụ điều phối gửi bản tin ISO8583 qua socket và báo cáo trạng thái hai kênh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Iso8583SocketService {

    private final Iso8583OutboundClient outboundClient;
    private final Iso8583InboundServer inboundServer;
    private final Iso8583PackagerProvider packagerProvider;
    private final Iso8583SocketProperties properties;

    /**
     * Dựng bản tin từ MTI + field rồi gửi qua kênh outbound, chờ response.
     */
    public Iso8583SendResponse send(Iso8583SendRequest request) {
        String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId()
                : "iso-out-" + UUID.randomUUID();

        ISOMsg isoRequest = buildMessage(request);
        Instant start = Instant.now();

        try {
            String requestHex = IsoMessageUtils.bytesToHex(packagerProvider.pack(isoRequest));

            if (request.isFireAndForget()) {
                outboundClient.sendAsync(isoRequest, correlationId);
                return Iso8583SendResponse.builder()
                        .correlationId(correlationId)
                        .target(outboundClient.getTargetAddress())
                        .requestMti(request.getMti())
                        .stan(IsoMessageUtils.getStan(isoRequest))
                        .requestHex(requestHex)
                        .roundTripMs(Duration.between(start, Instant.now()).toMillis())
                        .completedAt(LocalDateTime.now())
                        .build();
            }

            ISOMsg isoResponse = outboundClient.send(isoRequest, correlationId);
            byte[] responseRaw = packagerProvider.pack(isoResponse);

            return Iso8583SendResponse.builder()
                    .correlationId(correlationId)
                    .target(outboundClient.getTargetAddress())
                    .requestMti(request.getMti())
                    .stan(IsoMessageUtils.getStan(isoRequest))
                    .requestHex(requestHex)
                    .responseMti(IsoMessageUtils.safeGetMti(isoResponse))
                    .responseCode(isoResponse.hasField(IsoMessageUtils.FIELD_RESPONSE_CODE)
                            ? isoResponse.getString(IsoMessageUtils.FIELD_RESPONSE_CODE) : null)
                    .responseFields(IsoMessageUtils.toFieldMap(isoResponse))
                    .responseHex(IsoMessageUtils.bytesToHex(responseRaw))
                    .roundTripMs(Duration.between(start, Instant.now()).toMillis())
                    .completedAt(LocalDateTime.now())
                    .build();

        } catch (TimeoutException e) {
            throw new Iso8583Exception("Đối tác không trả lời: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Iso8583Exception("Bị ngắt khi chờ response ISO8583", e);
        } catch (Iso8583Exception e) {
            throw e;
        } catch (Exception e) {
            throw new Iso8583Exception("Gửi bản tin ISO8583 thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Gửi bản tin raw dạng hex (đã đóng gói sẵn) qua kênh outbound.
     */
    public Iso8583SendResponse sendRawHex(String hexMessage, String correlationId) {
        String cid = correlationId != null ? correlationId : "iso-out-raw-" + UUID.randomUUID();

        try {
            byte[] raw = IsoMessageUtils.hexToBytes(hexMessage);
            ISOMsg isoRequest = packagerProvider.unpack(raw);

            Iso8583SendRequest wrapper = new Iso8583SendRequest();
            wrapper.setMti(IsoMessageUtils.safeGetMti(isoRequest));
            wrapper.setCorrelationId(cid);

            Instant start = Instant.now();
            ISOMsg isoResponse = outboundClient.send(isoRequest, cid);

            return Iso8583SendResponse.builder()
                    .correlationId(cid)
                    .target(outboundClient.getTargetAddress())
                    .requestMti(wrapper.getMti())
                    .stan(IsoMessageUtils.getStan(isoRequest))
                    .requestHex(hexMessage.toUpperCase())
                    .responseMti(IsoMessageUtils.safeGetMti(isoResponse))
                    .responseCode(isoResponse.hasField(IsoMessageUtils.FIELD_RESPONSE_CODE)
                            ? isoResponse.getString(IsoMessageUtils.FIELD_RESPONSE_CODE) : null)
                    .responseFields(IsoMessageUtils.toFieldMap(isoResponse))
                    .responseHex(IsoMessageUtils.bytesToHex(packagerProvider.pack(isoResponse)))
                    .roundTripMs(Duration.between(start, Instant.now()).toMillis())
                    .completedAt(LocalDateTime.now())
                    .build();

        } catch (TimeoutException e) {
            throw new Iso8583Exception("Đối tác không trả lời: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Iso8583Exception("Bị ngắt khi chờ response ISO8583", e);
        } catch (IllegalArgumentException e) {
            throw new Iso8583Exception("Hex string không hợp lệ: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new Iso8583Exception("Gửi bản tin raw thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Trạng thái hiện tại của hai kênh socket.
     */
    public Iso8583SocketStatus getStatus() {
        return Iso8583SocketStatus.builder()
                .inbound(Iso8583SocketStatus.InboundStatus.builder()
                        .enabled(properties.getInbound().isEnabled())
                        .listening(inboundServer.isRunning())
                        .port(inboundServer.getListenPort())
                        .activeConnections(inboundServer.getActiveConnections().get())
                        .inFlightMessages(inboundServer.getInFlightMessages().get())
                        .availablePermits(inboundServer.getAvailablePermits())
                        .maxConcurrentMessages(properties.getInbound().getMaxConcurrentMessages())
                        .processingTimeoutMs(properties.getInbound().getProcessingTimeoutMs())
                        .totalReceived(inboundServer.getTotalMessagesReceived().get())
                        .totalFailed(inboundServer.getTotalMessagesFailed().get())
                        .totalRejected(inboundServer.getTotalMessagesRejected().get())
                        .totalTimedOut(inboundServer.getTotalMessagesTimedOut().get())
                        .build())
                .outbound(Iso8583SocketStatus.OutboundStatus.builder()
                        .enabled(properties.getOutbound().isEnabled())
                        .connected(outboundClient.isConnected())
                        .target(outboundClient.getTargetAddress())
                        .pendingRequests(outboundClient.getPendingRequestCount())
                        .maxPendingRequests(properties.getOutbound().getMaxPendingRequests())
                        .responseTimeoutMs(properties.getOutbound().getResponseTimeoutMs())
                        .totalSent(outboundClient.getTotalSent().get())
                        .totalReceived(outboundClient.getTotalReceived().get())
                        .totalTimeout(outboundClient.getTotalTimeout().get())
                        .build())
                .build();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private ISOMsg buildMessage(Iso8583SendRequest request) {
        try {
            ISOMsg msg = packagerProvider.newMessage();
            msg.setMTI(request.getMti());

            for (Map.Entry<Integer, String> entry : request.getFields().entrySet()) {
                int field = entry.getKey();
                if (field < 2 || field > 128) {
                    throw new Iso8583Exception("Số field không hợp lệ: " + field + " (phải từ 2-128)");
                }
                msg.set(field, entry.getValue());
            }
            return msg;

        } catch (Iso8583Exception e) {
            throw e;
        } catch (Exception e) {
            throw new Iso8583Exception("Không dựng được bản tin ISO8583: " + e.getMessage(), e);
        }
    }
}
