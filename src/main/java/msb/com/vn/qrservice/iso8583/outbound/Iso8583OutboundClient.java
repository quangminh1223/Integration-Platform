package msb.com.vn.qrservice.iso8583.outbound;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.codec.Iso8583FrameCodec;
import msb.com.vn.qrservice.iso8583.config.Iso8583PackagerProvider;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.audit.Iso8583AuditService;
import msb.com.vn.qrservice.iso8583.inbound.Iso8583HandlerDispatcher;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Luồng OUTBOUND — client kết nối tới đối tác (mặc định localhost:1112) để gửi bản tin ISO8583.
 *
 * <p>Kiến trúc:</p>
 * <ul>
 *   <li>Một socket persistent, tự động tái kết nối theo chu kỳ cấu hình</li>
 *   <li>Một reader thread đọc mọi frame về, correlate response theo STAN (field 11)</li>
 *   <li>{@link #send} là blocking có timeout; {@link #sendAsync} trả về CompletableFuture</li>
 *   <li>Ghi socket được đồng bộ bằng lock để tránh interleave frame</li>
 *   <li>Bản tin đến không khớp STAN nào (unsolicited) được đẩy sang handler dispatcher</li>
 * </ul>
 */
@Slf4j
@Component
public class Iso8583OutboundClient {

    private static final int STAN_MAX = 999999;
    private static final String MTI_NETWORK_REQUEST = "0800";
    private static final String NMI_ECHO_TEST = "301";

    private final Iso8583SocketProperties properties;
    private final Iso8583FrameCodec frameCodec;
    private final Iso8583PackagerProvider packagerProvider;
    private final Iso8583HandlerDispatcher dispatcher;
    private final Iso8583AuditService auditService;
    private final MeterRegistry meterRegistry;

    /** Map STAN → future đang chờ response */
    private final Map<String, CompletableFuture<ISOMsg>> pendingRequests = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicInteger stanSequence = new AtomicInteger(0);

    private volatile Socket socket;
    private volatile DataInputStream input;
    private volatile OutputStream output;
    private volatile boolean running;

    private ScheduledExecutorService scheduler;
    private Thread readerThread;

    /** Thời điểm bắt đầu lệnh ghi đang chạy; 0 = không có lệnh ghi nào */
    private final AtomicLong writeInProgressSince = new AtomicLong(0L);

    @Getter
    private final AtomicLong totalSent = new AtomicLong();
    @Getter
    private final AtomicLong totalReceived = new AtomicLong();
    @Getter
    private final AtomicLong totalTimeout = new AtomicLong();
    /** Số giao dịch bị từ chối vì không giành được quyền ghi (kênh nghẽn) */
    @Getter
    private final AtomicLong totalWriteBlocked = new AtomicLong();
    /** Số lần watchdog phải đóng socket vì lệnh ghi treo */
    @Getter
    private final AtomicLong totalWriteStalls = new AtomicLong();

    private Counter sentCounter;
    private Counter timeoutCounter;
    private Counter writeBlockedCounter;
    private Timer roundTripTimer;

    public Iso8583OutboundClient(Iso8583SocketProperties properties,
                                 Iso8583FrameCodec frameCodec,
                                 Iso8583PackagerProvider packagerProvider,
                                 Iso8583HandlerDispatcher dispatcher,
                                 Iso8583AuditService auditService,
                                 MeterRegistry meterRegistry) {
        this.properties = properties;
        this.frameCodec = frameCodec;
        this.packagerProvider = packagerProvider;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        Iso8583SocketProperties.Outbound cfg = properties.getOutbound();

        if (!cfg.isEnabled()) {
            log.info("ISO8583 outbound client đang TẮT (iso8583.socket.outbound.enabled=false)");
            return;
        }

        initMetrics();
        running = true;

        scheduler = Executors.newScheduledThreadPool(3, runnable -> {
            Thread t = new Thread(runnable, "iso8583-outbound-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Kết nối lần đầu — không chặn startup nếu đối tác chưa sẵn sàng
        scheduler.execute(this::tryConnect);

        // Giám sát và tái kết nối
        scheduler.scheduleWithFixedDelay(this::ensureConnected,
                cfg.getReconnectIntervalMs(), cfg.getReconnectIntervalMs(), TimeUnit.MILLISECONDS);

        // Watchdog phát hiện lệnh ghi treo — thiết yếu vì Java không có write timeout
        scheduler.scheduleWithFixedDelay(this::detectWriteStall,
                cfg.getWriteStallCheckIntervalMs(), cfg.getWriteStallCheckIntervalMs(),
                TimeUnit.MILLISECONDS);

        if (cfg.isEchoEnabled()) {
            scheduler.scheduleWithFixedDelay(this::sendEcho,
                    cfg.getEchoIntervalMs(), cfg.getEchoIntervalMs(), TimeUnit.MILLISECONDS);
            log.info("Bật echo test 0800 mỗi {} ms", cfg.getEchoIntervalMs());
        }

        log.info("ISO8583 outbound client cấu hình tới {}:{} (responseTimeout={} ms)",
                cfg.getHost(), cfg.getPort(), cfg.getResponseTimeoutMs());
    }

    @PreDestroy
    public void stop() {
        if (!running) {
            return;
        }
        log.info("Đang dừng ISO8583 outbound client...");
        running = false;

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        closeConnection();

        // Hủy mọi request đang chờ
        pendingRequests.forEach((stan, future) ->
                future.completeExceptionally(new IOException("Outbound client đang shutdown")));
        pendingRequests.clear();

        log.info("ISO8583 outbound client đã dừng");
    }

    // ─── API gửi bản tin ────────────────────────────────────────────────────

    /**
     * Gửi bản tin và chờ response (blocking, có timeout theo cấu hình).
     *
     * @param request       bản tin gửi đi; nếu chưa có field 11 sẽ được tự sinh STAN
     * @param correlationId ID truy vết
     * @return bản tin response từ đối tác
     * @throws IOException      lỗi kết nối hoặc gửi
     * @throws TimeoutException đối tác không trả lời trong thời gian cho phép
     */
    public ISOMsg send(ISOMsg request, String correlationId)
            throws IOException, TimeoutException, InterruptedException {

        long timeoutMs = properties.getOutbound().getResponseTimeoutMs();
        long startMs = System.currentTimeMillis();
        CompletableFuture<ISOMsg> future = sendAsync(request, correlationId);

        try {
            ISOMsg response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            auditService.logOutbound(request, response, correlationId, getTargetAddress(),
                    System.currentTimeMillis() - startMs, null);
            return response;

        } catch (TimeoutException e) {
            String stan = IsoMessageUtils.getStan(request);
            pendingRequests.remove(stan);
            totalTimeout.incrementAndGet();
            if (timeoutCounter != null) {
                timeoutCounter.increment();
            }
            log.error("Timeout chờ response: stan={}, correlationId={}, sau {} ms",
                    stan, correlationId, timeoutMs);
            auditService.logOutbound(request, null, correlationId, getTargetAddress(),
                    System.currentTimeMillis() - startMs,
                    "Timeout sau " + timeoutMs + "ms");
            throw new TimeoutException("Đối tác không trả lời sau " + timeoutMs + " ms (stan=" + stan + ")");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String reason = cause != null ? cause.getMessage() : e.getMessage();
            auditService.logOutbound(request, null, correlationId, getTargetAddress(),
                    System.currentTimeMillis() - startMs, reason);
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("Gửi bản tin ISO8583 thất bại: " + reason, cause);
        }
    }

    /**
     * Gửi bản tin không chờ response.
     */
    public CompletableFuture<ISOMsg> sendAsync(ISOMsg request, String correlationId) throws IOException {
        if (!running) {
            throw new IOException("Outbound client chưa khởi động hoặc đã dừng");
        }

        ensureConnectedOrThrow();

        String stan;
        byte[] raw;
        try {
            if (!request.hasField(IsoMessageUtils.FIELD_STAN)) {
                request.set(IsoMessageUtils.FIELD_STAN, nextStan());
            }
            IsoMessageUtils.fillTimeFields(request);
            stan = IsoMessageUtils.getStan(request);
            raw = packagerProvider.pack(request);
        } catch (Exception e) {
            throw new IOException("Không đóng gói được bản tin ISO8583: " + e.getMessage(), e);
        }

        int maxPending = properties.getOutbound().getMaxPendingRequests();
        if (pendingRequests.size() >= maxPending) {
            throw new IOException("Outbound quá tải: " + pendingRequests.size()
                    + " request đang chờ response, đã đạt trần maxPendingRequests=" + maxPending);
        }

        CompletableFuture<ISOMsg> future = new CompletableFuture<>();
        CompletableFuture<ISOMsg> existing = pendingRequests.putIfAbsent(stan, future);
        if (existing != null) {
            throw new IOException("STAN " + stan + " đang có request chờ response — trùng khóa correlate");
        }

        Instant start = Instant.now();
        future.whenComplete((response, error) -> {
            if (roundTripTimer != null) {
                roundTripTimer.record(Duration.between(start, Instant.now()));
            }
        });

        writeFrameGuarded(raw, stan, request, correlationId);
        return future;
    }

    /**
     * Ghi frame ra socket với hai lớp bảo vệ chống treo:
     *
     * <ol>
     *   <li>{@code tryLock} có hạn — không chờ vô hạn khi lệnh ghi khác đang bị treo,
     *       nên một giao dịch treo không chặn được các giao dịch sau</li>
     *   <li>Đánh dấu {@code writeInProgressSince} để watchdog phát hiện lệnh ghi treo
     *       và đóng socket cưỡng chế</li>
     * </ol>
     */
    private void writeFrameGuarded(byte[] raw, String stan, ISOMsg request, String correlationId)
            throws IOException {

        Iso8583SocketProperties.Outbound cfg = properties.getOutbound();
        boolean acquired;
        try {
            acquired = writeLock.tryLock(cfg.getWriteAcquireTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingRequests.remove(stan);
            throw new IOException("Bị ngắt khi chờ quyền ghi socket outbound", e);
        }

        if (!acquired) {
            pendingRequests.remove(stan);
            totalWriteBlocked.incrementAndGet();
            if (writeBlockedCounter != null) {
                writeBlockedCounter.increment();
            }
            log.error("Không giành được quyền ghi socket sau {}ms — đối tác {} có thể đang treo, "
                            + "không nhận dữ liệu. stan={}, correlationId={}",
                    cfg.getWriteAcquireTimeoutMs(), getTargetAddress(), stan, correlationId);
            throw new IOException("Kênh outbound tới " + getTargetAddress()
                    + " đang bị nghẽn: không giành được quyền ghi sau "
                    + cfg.getWriteAcquireTimeoutMs() + "ms");
        }

        try {
            writeInProgressSince.set(System.currentTimeMillis());
            frameCodec.writeFrame(output, raw);

            totalSent.incrementAndGet();
            if (sentCounter != null) {
                sentCounter.increment();
            }
            log.info("Đã gửi ISO8583 ({} byte) tới {} — {} correlationId={}",
                    raw.length, getTargetAddress(),
                    IsoMessageUtils.summarize(request), correlationId);

        } catch (IOException e) {
            pendingRequests.remove(stan);
            log.error("Lỗi ghi socket outbound, sẽ tái kết nối: {}", e.getMessage());
            closeConnection();
            throw e;
        } finally {
            writeInProgressSince.set(0L);
            writeLock.unlock();
        }
    }

    /**
     * Watchdog: phát hiện lệnh ghi bị treo và đóng socket cưỡng chế.
     *
     * <p>Đóng socket từ thread khác là cách DUY NHẤT phá vỡ một blocking write trong
     * Java — lệnh ghi đang treo sẽ bung IOException, thread được giải phóng, và
     * vòng tái kết nối sẽ dựng lại kênh.</p>
     */
    private void detectWriteStall() {
        long since = writeInProgressSince.get();
        if (since == 0L) {
            return;
        }

        long elapsed = System.currentTimeMillis() - since;
        long limit = properties.getOutbound().getWriteStallTimeoutMs();
        if (elapsed <= limit) {
            return;
        }

        totalWriteStalls.incrementAndGet();
        log.error("PHÁT HIỆN GHI TREO: lệnh ghi socket tới {} đã kéo dài {}ms (giới hạn {}ms). "
                        + "Đóng socket cưỡng chế để giải phóng thread và tái kết nối.",
                getTargetAddress(), elapsed, limit);

        // Đóng socket → blocking write bung IOException → thread thoát
        closeConnection();
        failPendingRequests();
    }

    /**
     * Sinh STAN 6 chữ số quay vòng.
     */
    public String nextStan() {
        int value = stanSequence.updateAndGet(prev -> prev >= STAN_MAX ? 1 : prev + 1);
        return String.format("%06d", value);
    }

    // ─── Quản lý kết nối ────────────────────────────────────────────────────

    public boolean isConnected() {
        Socket current = socket;
        return current != null && current.isConnected() && !current.isClosed();
    }

    public String getTargetAddress() {
        Iso8583SocketProperties.Outbound cfg = properties.getOutbound();
        return cfg.getHost() + ":" + cfg.getPort();
    }

    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    private void ensureConnected() {
        if (running && !isConnected()) {
            log.debug("Outbound chưa kết nối, thử kết nối lại tới {}", getTargetAddress());
            tryConnect();
        }
    }

    private void ensureConnectedOrThrow() throws IOException {
        if (!isConnected()) {
            synchronized (this) {
                if (!isConnected()) {
                    connect();
                }
            }
        }
    }

    private void tryConnect() {
        try {
            synchronized (this) {
                if (!isConnected()) {
                    connect();
                }
            }
        } catch (IOException e) {
            log.warn("Không kết nối được tới {} — {}", getTargetAddress(), e.getMessage());
        }
    }

    private void connect() throws IOException {
        Iso8583SocketProperties.Outbound cfg = properties.getOutbound();

        Socket newSocket = new Socket();
        newSocket.setTcpNoDelay(cfg.isTcpNoDelay());
        newSocket.setKeepAlive(cfg.isKeepAlive());
        if (cfg.getSoTimeoutMs() > 0) {
            newSocket.setSoTimeout(cfg.getSoTimeoutMs());
        }
        newSocket.connect(new InetSocketAddress(cfg.getHost(), cfg.getPort()), cfg.getConnectTimeoutMs());

        this.socket = newSocket;
        this.input = new DataInputStream(newSocket.getInputStream());
        this.output = new BufferedOutputStream(newSocket.getOutputStream());

        startReaderThread();
        log.info("Đã kết nối ISO8583 outbound tới {}", getTargetAddress());
    }

    private void startReaderThread() {
        Thread previous = readerThread;
        if (previous != null && previous.isAlive()) {
            previous.interrupt();
        }
        readerThread = new Thread(this::readerLoop, "iso8583-outbound-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void closeConnection() {
        Socket current = socket;
        socket = null;
        input = null;
        output = null;

        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // bỏ qua
            }
        }
    }

    // ─── Reader loop ────────────────────────────────────────────────────────

    private void readerLoop() {
        DataInputStream in = this.input;
        log.debug("Reader thread outbound bắt đầu");

        while (running && in != null && isConnected()) {
            try {
                byte[] raw = frameCodec.readFrame(in);

                if (raw == null) {
                    log.warn("Đối tác {} đã đóng kết nối outbound", getTargetAddress());
                    break;
                }

                totalReceived.incrementAndGet();
                handleIncomingFrame(raw);

            } catch (IOException e) {
                if (running) {
                    log.warn("Lỗi đọc socket outbound: {}", e.getMessage());
                }
                break;
            } catch (Exception e) {
                log.error("Lỗi không mong đợi trong reader outbound: {}", e.getMessage(), e);
            }
        }

        closeConnection();
        failPendingRequests();
        log.debug("Reader thread outbound kết thúc");
    }

    private void handleIncomingFrame(byte[] raw) {
        try {
            ISOMsg message = packagerProvider.unpack(raw);
            String stan = IsoMessageUtils.getStan(message);

            log.debug("Nhận frame outbound ({} byte): {}", raw.length, IsoMessageUtils.summarize(message));

            if (stan != null) {
                CompletableFuture<ISOMsg> future = pendingRequests.remove(stan);
                if (future != null) {
                    future.complete(message);
                    log.info("Khớp response stan={} — {}", stan, IsoMessageUtils.summarize(message));
                    return;
                }
            }

            // Không khớp request nào → bản tin do đối tác chủ động gửi
            handleUnsolicited(message, stan);

        } catch (Exception e) {
            log.error("Không parse được frame outbound ({} byte): {}", raw.length, e.getMessage());
        }
    }

    private void handleUnsolicited(ISOMsg message, String stan) {
        String correlationId = "iso-out-unsol-" + (stan != null ? stan : System.nanoTime());
        log.info("Bản tin unsolicited trên kênh outbound: {} correlationId={}",
                IsoMessageUtils.summarize(message), correlationId);

        Iso8583Exchange exchange = new Iso8583Exchange(
                correlationId,
                getTargetAddress(),
                Instant.now(),
                null,
                message,
                IsoMessageUtils.safeGetMti(message),
                stan);

        ISOMsg response = dispatcher.dispatch(exchange);
        if (response == null) {
            return;
        }

        boolean acquired = false;
        try {
            acquired = writeLock.tryLock(
                    properties.getOutbound().getWriteAcquireTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.error("Không gửi được response unsolicited — kênh outbound đang nghẽn");
                return;
            }
            writeInProgressSince.set(System.currentTimeMillis());
            frameCodec.writeFrame(output, packagerProvider.pack(response));
            log.info("Đã trả response cho bản tin unsolicited: {}", IsoMessageUtils.summarize(response));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Không gửi được response cho bản tin unsolicited: {}", e.getMessage());
        } finally {
            if (acquired) {
                writeInProgressSince.set(0L);
                writeLock.unlock();
            }
        }
    }

    private void failPendingRequests() {
        if (pendingRequests.isEmpty()) {
            return;
        }
        log.warn("Hủy {} request đang chờ vì kết nối đã đóng", pendingRequests.size());
        pendingRequests.forEach((stan, future) ->
                future.completeExceptionally(new IOException("Kết nối outbound đã đóng khi đang chờ response")));
        pendingRequests.clear();
    }

    // ─── Echo test ──────────────────────────────────────────────────────────

    private void sendEcho() {
        if (!isConnected()) {
            return;
        }
        try {
            ISOMsg echo = packagerProvider.newMessage();
            echo.setMTI(MTI_NETWORK_REQUEST);
            echo.set(IsoMessageUtils.FIELD_NMI_CODE, NMI_ECHO_TEST);

            ISOMsg response = send(echo, "iso-out-echo-" + System.currentTimeMillis());
            log.debug("Echo test OK: rc={}",
                    response.hasField(IsoMessageUtils.FIELD_RESPONSE_CODE)
                            ? response.getString(IsoMessageUtils.FIELD_RESPONSE_CODE) : "-");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Echo test thất bại: {}", e.getMessage());
        }
    }

    // ─── Metrics ────────────────────────────────────────────────────────────

    private void initMetrics() {
        sentCounter = Counter.builder("iso8583.outbound.messages")
                .tag("result", "sent")
                .description("Số bản tin ISO8583 gửi qua socket outbound")
                .register(meterRegistry);

        timeoutCounter = Counter.builder("iso8583.outbound.messages")
                .tag("result", "timeout")
                .description("Số bản tin ISO8583 outbound bị timeout")
                .register(meterRegistry);

        writeBlockedCounter = Counter.builder("iso8583.outbound.messages")
                .tag("result", "write_blocked")
                .description("Số bản tin bị từ chối vì kênh outbound nghẽn (đối tác không đọc socket)")
                .register(meterRegistry);

        meterRegistry.gauge("iso8583.outbound.write.stalls", totalWriteStalls);

        roundTripTimer = Timer.builder("iso8583.outbound.roundtrip")
                .description("Thời gian round-trip request/response ISO8583 outbound")
                .register(meterRegistry);

        meterRegistry.gauge("iso8583.outbound.pending", pendingRequests, Map::size);
    }
}
