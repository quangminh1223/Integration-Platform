package msb.com.vn.qrservice.iso8583.inbound;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.audit.Iso8583AuditService;
import msb.com.vn.qrservice.iso8583.audit.IsoAuditEntry;
import msb.com.vn.qrservice.iso8583.codec.Iso8583FrameCodec;
import msb.com.vn.qrservice.iso8583.config.Iso8583PackagerProvider;
import msb.com.vn.qrservice.iso8583.config.Iso8583SocketProperties;
import msb.com.vn.qrservice.iso8583.idempotent.IsoIdempotencyGuard;
import msb.com.vn.qrservice.iso8583.pending.PendingTransactionService;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.transform.Iso8583JsonConverter;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Luồng INBOUND — ứng dụng này mở ServerSocket (mặc định port 1111) và NHẬN bản tin
 * ISO8583 do hệ thống bên ngoài gửi đến.
 *
 * <h2>Mô hình xử lý (pipelining)</h2>
 * <ol>
 *   <li>Một acceptor thread chạy vòng lặp {@code accept()}</li>
 *   <li>Mỗi connection có một reader thread (virtual) chỉ làm việc đọc frame — không xử lý nghiệp vụ</li>
 *   <li>Mỗi bản tin đọc được submit sang worker (virtual thread) riêng → nhiều bản tin
 *       trên CÙNG một connection được xử lý song song</li>
 *   <li>Response ghi lại qua {@code writeLock} của connection để không interleave frame</li>
 *   <li>Bên gọi correlate response bằng STAN (field 11), nên thứ tự trả về không cần khớp thứ tự nhận</li>
 * </ol>
 *
 * <h2>Bảo vệ tài nguyên</h2>
 * <ul>
 *   <li><b>Backpressure</b>: semaphore giới hạn số bản tin đang xử lý đồng thời.
 *       Vượt trần → trả ngay RC 96 thay vì xếp hàng vô hạn (fail fast)</li>
 *   <li><b>Deadline</b>: watchdog trả RC 68 nếu handler vượt {@code processingTimeoutMs},
 *       bên gọi không bị treo chờ</li>
 *   <li><b>Drain khi shutdown</b>: chờ các bản tin đang xử lý hoàn tất trước khi đóng socket</li>
 * </ul>
 *
 * <p>Sizing theo định luật Little: {@code maxConcurrentMessages = TPS mục tiêu × timeout(giây)}.
 * Ví dụ 400 TPS với timeout 15s → 6000.</p>
 */
@Slf4j
@Component
public class Iso8583InboundServer {

    private static final String MDC_CORRELATION_ID = "correlationId";

    private final Iso8583SocketProperties properties;
    private final Iso8583FrameCodec frameCodec;
    private final Iso8583PackagerProvider packagerProvider;
    private final Iso8583HandlerDispatcher dispatcher;
    private final Iso8583AuditService auditService;
    private final Iso8583JsonConverter jsonConverter;
    private final IsoIdempotencyGuard idempotencyGuard;
    private final PendingTransactionService pendingTransactionService;
    private final MeterRegistry meterRegistry;

    private ServerSocket serverSocket;
    /** Executor cho reader thread của mỗi connection */
    private ExecutorService connectionExecutor;
    /** Executor cho worker xử lý từng bản tin */
    private ExecutorService messageExecutor;
    /** Watchdog cưỡng chế deadline xử lý */
    private ScheduledExecutorService deadlineScheduler;
    /** Giới hạn số bản tin xử lý đồng thời toàn server */
    private Semaphore concurrencyLimiter;

    private Thread acceptorThread;
    private volatile boolean running;

    @Getter
    private final AtomicInteger activeConnections = new AtomicInteger();
    @Getter
    private final AtomicInteger inFlightMessages = new AtomicInteger();
    @Getter
    private final AtomicLong totalMessagesReceived = new AtomicLong();
    @Getter
    private final AtomicLong totalMessagesFailed = new AtomicLong();
    @Getter
    private final AtomicLong totalMessagesRejected = new AtomicLong();
    @Getter
    private final AtomicLong totalMessagesTimedOut = new AtomicLong();
    /** Số response không ghi được vì bên gọi ngừng đọc socket */
    @Getter
    private final AtomicLong totalWriteBlocked = new AtomicLong();
    /** Số bản tin trùng lặp bị chặn không cho chạm CORE lần hai */
    @Getter
    private final AtomicLong totalMessagesDuplicate = new AtomicLong();

    private Counter receivedCounter;
    private Counter failedCounter;
    private Counter rejectedCounter;
    private Counter timeoutCounter;
    private Counter writeBlockedCounter;
    private Counter duplicateCounter;
    private Timer processingTimer;

    public Iso8583InboundServer(Iso8583SocketProperties properties,
                                Iso8583FrameCodec frameCodec,
                                Iso8583PackagerProvider packagerProvider,
                                Iso8583HandlerDispatcher dispatcher,
                                Iso8583AuditService auditService,
                                Iso8583JsonConverter jsonConverter,
                                IsoIdempotencyGuard idempotencyGuard,
                                PendingTransactionService pendingTransactionService,
                                MeterRegistry meterRegistry) {
        this.properties = properties;
        this.frameCodec = frameCodec;
        this.packagerProvider = packagerProvider;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
        this.jsonConverter = jsonConverter;
        this.idempotencyGuard = idempotencyGuard;
        this.pendingTransactionService = pendingTransactionService;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        Iso8583SocketProperties.Inbound cfg = properties.getInbound();

        if (!cfg.isEnabled()) {
            log.info("ISO8583 inbound server đang TẮT (iso8583.socket.inbound.enabled=false)");
            return;
        }

        concurrencyLimiter = new Semaphore(cfg.getMaxConcurrentMessages(), false);
        initMetrics();

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(cfg.getBindAddress(), cfg.getPort()), cfg.getBacklog());

            connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();
            messageExecutor = Executors.newVirtualThreadPerTaskExecutor();
            deadlineScheduler = Executors.newScheduledThreadPool(
                    cfg.getDeadlineSchedulerThreads(), r -> {
                        Thread t = new Thread(r, "iso8583-inbound-deadline");
                        t.setDaemon(true);
                        return t;
                    });

            running = true;

            acceptorThread = new Thread(this::acceptLoop, "iso8583-inbound-acceptor");
            acceptorThread.setDaemon(true);
            acceptorThread.start();

            log.info("ISO8583 inbound server lắng nghe tại {}:{} | pipelining={} | "
                            + "maxConcurrentMessages={} | processingTimeout={}ms | maxConnections={}",
                    cfg.getBindAddress(), cfg.getPort(), cfg.isPipeliningEnabled(),
                    cfg.getMaxConcurrentMessages(), cfg.getProcessingTimeoutMs(), cfg.getMaxConnections());

        } catch (IOException e) {
            log.error("Không thể mở ISO8583 inbound server tại {}:{} — {}",
                    cfg.getBindAddress(), cfg.getPort(), e.getMessage(), e);
            throw new IllegalStateException("Khởi động ISO8583 inbound server thất bại", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (!running) {
            return;
        }
        log.info("Đang dừng ISO8583 inbound server ({} bản tin đang xử lý)...", inFlightMessages.get());
        running = false;

        closeQuietly(serverSocket);
        shutdownGracefully(connectionExecutor, "connectionExecutor");
        shutdownGracefully(messageExecutor, "messageExecutor");

        if (deadlineScheduler != null) {
            deadlineScheduler.shutdownNow();
        }
        log.info("ISO8583 inbound server đã dừng");
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public int getListenPort() {
        return properties.getInbound().getPort();
    }

    /** Số slot xử lý còn trống — 0 nghĩa là đang bị backpressure */
    public int getAvailablePermits() {
        return concurrencyLimiter != null ? concurrencyLimiter.availablePermits() : 0;
    }

    // ─── Acceptor ───────────────────────────────────────────────────────────

    private void acceptLoop() {
        Iso8583SocketProperties.Inbound cfg = properties.getInbound();

        while (running) {
            try {
                Socket socket = serverSocket.accept();

                if (activeConnections.get() >= cfg.getMaxConnections()) {
                    log.warn("Từ chối kết nối từ {} — đã đạt maxConnections={}",
                            socket.getRemoteSocketAddress(), cfg.getMaxConnections());
                    closeQuietly(socket);
                    continue;
                }

                socket.setTcpNoDelay(cfg.isTcpNoDelay());
                socket.setKeepAlive(cfg.isKeepAlive());
                if (cfg.getSoTimeoutMs() > 0) {
                    socket.setSoTimeout(cfg.getSoTimeoutMs());
                }

                connectionExecutor.submit(() -> readerLoop(socket));

            } catch (IOException e) {
                if (running) {
                    log.error("Lỗi accept connection: {}", e.getMessage());
                }
                // running=false → socket đã đóng khi shutdown, thoát vòng lặp
            }
        }
    }

    // ─── Reader thread: chỉ đọc frame, không xử lý nghiệp vụ ────────────────

    private void readerLoop(Socket socket) {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        Iso8583SocketProperties.Inbound cfg = properties.getInbound();

        activeConnections.incrementAndGet();
        log.info("Nhận kết nối ISO8583 từ {} (activeConnections={})", remote, activeConnections.get());

        ConnectionContext ctx = null;

        try (Socket managed = socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(managed.getInputStream()));
             OutputStream out = new BufferedOutputStream(managed.getOutputStream())) {

            ctx = new ConnectionContext(remote, managed, out);

            while (running && !managed.isClosed()) {
                byte[] rawRequest = frameCodec.readFrame(in);

                if (rawRequest == null) {
                    log.info("Bên gọi {} đã đóng kết nối", remote);
                    break;
                }

                totalMessagesReceived.incrementAndGet();
                receivedCounter.increment();

                if (cfg.isPipeliningEnabled()) {
                    submitForProcessing(ctx, rawRequest);
                } else {
                    processMessage(ctx, rawRequest);
                }
            }

        } catch (IOException e) {
            log.warn("Kết nối {} kết thúc do lỗi I/O: {}", remote, e.getMessage());
        } catch (Exception e) {
            log.error("Lỗi không mong đợi trên kết nối {}: {}", remote, e.getMessage(), e);
        } finally {
            // Chờ các bản tin của connection này xử lý xong trước khi coi là đã đóng
            if (ctx != null) {
                ctx.awaitDrain(cfg.getDrainTimeoutMs());
            }
            activeConnections.decrementAndGet();
            log.info("Đóng kết nối {} (activeConnections={})", remote, activeConnections.get());
        }
    }

    /**
     * Đưa bản tin sang worker riêng. Trả về ngay để reader tiếp tục đọc bản tin kế tiếp.
     * Nếu hết slot xử lý, trả response từ chối ngay tại đây (fail fast).
     */
    private void submitForProcessing(ConnectionContext ctx, byte[] rawRequest) {
        if (!concurrencyLimiter.tryAcquire()) {
            handleBackpressure(ctx, rawRequest);
            return;
        }

        ctx.inFlight.incrementAndGet();
        inFlightMessages.incrementAndGet();

        try {
            messageExecutor.submit(() -> {
                try {
                    processMessage(ctx, rawRequest);
                } finally {
                    concurrencyLimiter.release();
                    ctx.inFlight.decrementAndGet();
                    inFlightMessages.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            // Executor đã shutdown
            concurrencyLimiter.release();
            ctx.inFlight.decrementAndGet();
            inFlightMessages.decrementAndGet();
            log.warn("Không submit được bản tin từ {} — executor đã dừng", ctx.remote);
        }
    }

    /**
     * Quá tải: không dispatch nghiệp vụ, trả response code cấu hình sẵn để bên gọi retry.
     */
    private void handleBackpressure(ConnectionContext ctx, byte[] rawRequest) {
        totalMessagesRejected.incrementAndGet();
        rejectedCounter.increment();

        String correlationId = generateCorrelationId();
        log.warn("QUÁ TẢI — từ chối bản tin từ {} (inFlight={}, permits=0), correlationId={}",
                ctx.remote, inFlightMessages.get(), correlationId);

        String rejectCode = properties.getInbound().getBackpressureResponseCode();

        try {
            ISOMsg request = packagerProvider.unpack(rawRequest);
            ISOMsg reject = dispatcher.buildRejectResponse(request, rejectCode, correlationId);
            writeResponse(ctx, reject, correlationId);

            // Vẫn ghi audit cho giao dịch bị từ chối — cần cho đối soát
            Iso8583Exchange exchange = new Iso8583Exchange(
                    correlationId, ctx.remote, Instant.now(), rawRequest, request,
                    IsoMessageUtils.safeGetMti(request), IsoMessageUtils.getStan(request));
            exchange.setResponseCode(rejectCode);
            auditService.logFailure(exchange, IsoAuditEntry.Phase.REJECTED,
                    "Quá tải: hết slot xử lý (maxConcurrentMessages="
                            + properties.getInbound().getMaxConcurrentMessages() + ")");

        } catch (Exception e) {
            log.error("Không trả được response quá tải cho {}: {}", ctx.remote, e.getMessage());
        }
    }

    // ─── Worker: xử lý một bản tin ──────────────────────────────────────────

    /**
     * Pipeline xử lý một bản tin:
     * <pre>
     * unpack → AUDIT(queue) → convert ISO→JSON → business flow → AUDIT(queue) → ghi response
     * </pre>
     * Bước audit là non-blocking, không nằm trên đường trả response.
     */
    private void processMessage(ConnectionContext ctx, byte[] rawRequest) {
        String correlationId = generateCorrelationId();
        MDC.put(MDC_CORRELATION_ID, correlationId);
        long startNanos = System.nanoTime();
        Iso8583Exchange exchange = null;
        String idempotencyKey = null;

        try {
            log.debug("Bản tin đến từ {} ({} byte): {}",
                    ctx.remote, rawRequest.length, IsoMessageUtils.toPrintableAscii(rawRequest));

            // ── 1. Unpack ───────────────────────────────────────────────────
            ISOMsg request = packagerProvider.unpack(rawRequest);
            exchange = new Iso8583Exchange(
                    correlationId,
                    ctx.remote,
                    Instant.now(),
                    rawRequest,
                    request,
                    IsoMessageUtils.safeGetMti(request),
                    IsoMessageUtils.getStan(request));

            // ── 2. Audit: đẩy vào queue để log giao dịch (non-blocking) ─────
            //     Làm trước khi convert để có dấu vết kể cả khi các bước sau lỗi
            auditService.logReceived(exchange);

            // ── 3. Idempotency: chặn hạch toán hai lần ──────────────────────
            //     CORE không đảo được nên bản tin gửi lại TUYỆT ĐỐI không được chạm CORE lần hai
            IsoIdempotencyGuard.GuardResult guard = idempotencyGuard.check(exchange);
            idempotencyKey = guard.getKey();

            if (guard.isDuplicate()) {
                totalMessagesDuplicate.incrementAndGet();
                duplicateCounter.increment();
                ISOMsg replay = idempotencyGuard.buildDuplicateResponse(exchange, guard);
                writeResponse(ctx, replay, correlationId);
                auditService.logFailure(exchange, IsoAuditEntry.Phase.FAILED,
                        "Bản tin trùng lặp, trạng thái bản gốc: " + guard.getOriginalState());
                return;
            }

            // ── 4. Transformation: ISO → JSON ───────────────────────────────
            if (properties.getTransform().isIsoToJsonEnabled()) {
                exchange.setJsonRequest(jsonConverter.toJson(
                        request, properties.getTransform().isIncludeFieldNames()));
                log.debug("Đã convert ISO→JSON, correlationId={}", correlationId);
            }

            // Chốt một lần trả response duy nhất: worker HOẶC watchdog, không cả hai
            AtomicBoolean responded = new AtomicBoolean(false);
            ScheduledFuture<?> watchdog = scheduleDeadline(ctx, exchange, responded, idempotencyKey);

            try {
                // ── 5. Business flow (gọi CORE) ─────────────────────────────
                ISOMsg response = dispatcher.dispatch(exchange);

                if (response == null) {
                    log.debug("Không có response cho bản tin này, correlationId={}", correlationId);
                    idempotencyGuard.release(idempotencyKey);
                    return;
                }

                exchange.setIsoResponse(response);
                String responseCode = response.hasField(IsoMessageUtils.FIELD_RESPONSE_CODE)
                        ? response.getString(IsoMessageUtils.FIELD_RESPONSE_CODE) : null;
                exchange.setResponseCode(responseCode);
                exchange.setCompletedAt(Instant.now());

                if (responded.compareAndSet(false, true)) {
                    // ── 6. Kịp deadline: trả response + chốt idempotency ────
                    writeResponse(ctx, response, correlationId);
                    idempotencyGuard.markCompleted(idempotencyKey, response, responseCode);
                    auditService.logCompleted(exchange);
                } else {
                    // ── CORE trả về MUỘN, sau khi watchdog đã trả RC 68 ─────
                    //    Đây là thông tin quyết định cho đối soát: response code của CORE
                    //    cho biết tiền có bị trừ hay không. Tuyệt đối không được bỏ.
                    pendingTransactionService.recordLateCoreResponse(exchange, responseCode);
                }
            } finally {
                if (watchdog != null) {
                    watchdog.cancel(false);
                }
            }

        } catch (Exception e) {
            totalMessagesFailed.incrementAndGet();
            failedCounter.increment();
            log.error("Lỗi xử lý bản tin từ {} correlationId={}: {}",
                    ctx.remote, correlationId, e.getMessage(), e);
            if (exchange != null) {
                auditService.logFailure(exchange, IsoAuditEntry.Phase.FAILED, e.getMessage());
            }
            // Lỗi ở tầng platform (unpack, transform) nghĩa là chưa chạm CORE
            // → giải phóng khóa để bên gọi được phép gửi lại
            idempotencyGuard.release(idempotencyKey);
        } finally {
            processingTimer.record(Duration.ofNanos(System.nanoTime() - startNanos));
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    /**
     * Hẹn watchdog: quá deadline mà worker chưa trả response thì watchdog trả thay,
     * để bên gọi không phải chờ đến hết timeout của họ.
     */
    private ScheduledFuture<?> scheduleDeadline(ConnectionContext ctx,
                                                Iso8583Exchange exchange,
                                                AtomicBoolean responded,
                                                String idempotencyKey) {
        Iso8583SocketProperties.Inbound cfg = properties.getInbound();
        if (cfg.getProcessingTimeoutMs() <= 0 || deadlineScheduler == null) {
            return null;
        }
        String correlationId = exchange.getCorrelationId();
        String timeoutRc = cfg.getTimeoutResponseCode();

        return deadlineScheduler.schedule(() -> {
            if (!responded.compareAndSet(false, true)) {
                return; // worker đã trả response, không làm gì
            }
            totalMessagesTimedOut.incrementAndGet();
            timeoutCounter.increment();
            log.error("QUÁ DEADLINE {}ms — trả RC {} cho {}, correlationId={}",
                    cfg.getProcessingTimeoutMs(), timeoutRc, ctx.remote, correlationId);

            // Giữ khóa idempotency ở trạng thái UNKNOWN: CORE vẫn đang xử lý và
            // có thể hạch toán thành công. Bên gọi gửi lại phải bị chặn, không được
            // để chạm CORE lần hai vì CORE không hỗ trợ bản tin đảo.
            idempotencyGuard.markUnknown(idempotencyKey, timeoutRc);

            // Ghi vào bảng đối soát — đây là dấu vết duy nhất cho lệch quỹ tiềm ẩn
            exchange.setResponseCode(timeoutRc);
            pendingTransactionService.recordTimeout(exchange, timeoutRc, cfg.getProcessingTimeoutMs());

            // Đẩy việc ghi socket sang virtual thread: writeFrame có thể block nếu
            // peer đọc chậm, không được để nó chiếm thread của scheduler — nếu không
            // watchdog của các bản tin khác sẽ bị trễ và deadline mất hiệu lực.
            try {
                messageExecutor.submit(() -> {
                    ISOMsg lateResponse = dispatcher.buildRejectResponse(
                            exchange.getIsoRequest(), timeoutRc, correlationId);
                    writeResponse(ctx, lateResponse, correlationId);

                    auditService.logFailure(exchange, IsoAuditEntry.Phase.TIMED_OUT,
                            "Vượt deadline xử lý " + cfg.getProcessingTimeoutMs() + "ms");
                });
            } catch (RejectedExecutionException e) {
                log.warn("Không gửi được response quá deadline (executor đã dừng), correlationId={}",
                        correlationId);
            }

        }, cfg.getProcessingTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Ghi response ra socket. Lock theo connection để hai worker không interleave frame.
     */
    private void writeResponse(ConnectionContext ctx, ISOMsg response, String correlationId) {
        if (response == null) {
            return;
        }

        long acquireTimeout = properties.getInbound().getWriteAcquireTimeoutMs();
        boolean acquired = false;

        try {
            acquired = ctx.writeLock.tryLock(acquireTimeout, TimeUnit.MILLISECONDS);

            if (!acquired) {
                // Bên gọi ngừng đọc socket → lệnh ghi của thread khác đang block vĩnh viễn
                // (Java blocking I/O không có write timeout). Đóng connection để vừa giải phóng
                // slot xử lý của thread này, vừa phá vỡ lệnh ghi đang treo kia.
                totalWriteBlocked.incrementAndGet();
                writeBlockedCounter.increment();
                log.error("Không giành được quyền ghi cho {} sau {}ms — bên gọi không đọc socket. "
                                + "Đóng connection để giải phóng. correlationId={}",
                        ctx.remote, acquireTimeout, correlationId);
                ctx.closeSocket();
                return;
            }

            byte[] raw = packagerProvider.pack(response);
            frameCodec.writeFrame(ctx.output, raw);
            log.info("Đã trả response {} byte cho {} — {} correlationId={}",
                    raw.length, ctx.remote, IsoMessageUtils.summarize(response), correlationId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Bị ngắt khi chờ quyền ghi response cho {} correlationId={}",
                    ctx.remote, correlationId);
        } catch (Exception e) {
            log.error("Không ghi được response cho {} correlationId={}: {}",
                    ctx.remote, correlationId, e.getMessage());
        } finally {
            if (acquired) {
                ctx.writeLock.unlock();
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String generateCorrelationId() {
        return "iso-in-" + UUID.randomUUID();
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // bỏ qua khi shutdown
        }
    }

    private void shutdownGracefully(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            long waitMs = properties.getInbound().getDrainTimeoutMs();
            if (!executor.awaitTermination(waitMs, TimeUnit.MILLISECONDS)) {
                log.warn("{} chưa dừng hết sau {}ms, buộc dừng", name, waitMs);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void initMetrics() {
        receivedCounter = Counter.builder("iso8583.inbound.messages")
                .tag("result", "received")
                .description("Số bản tin ISO8583 nhận được qua socket inbound")
                .register(meterRegistry);

        failedCounter = Counter.builder("iso8583.inbound.messages")
                .tag("result", "failed")
                .description("Số bản tin ISO8583 inbound xử lý lỗi")
                .register(meterRegistry);

        rejectedCounter = Counter.builder("iso8583.inbound.messages")
                .tag("result", "rejected")
                .description("Số bản tin ISO8583 inbound bị từ chối do quá tải")
                .register(meterRegistry);

        timeoutCounter = Counter.builder("iso8583.inbound.messages")
                .tag("result", "timeout")
                .description("Số bản tin ISO8583 inbound vượt deadline xử lý")
                .register(meterRegistry);

        writeBlockedCounter = Counter.builder("iso8583.inbound.messages")
                .tag("result", "write_blocked")
                .description("Số response không ghi được vì bên gọi ngừng đọc socket")
                .register(meterRegistry);

        duplicateCounter = Counter.builder("iso8583.inbound.messages")
                .tag("result", "duplicate")
                .description("Số bản tin trùng lặp bị chặn, không cho chạm CORE lần hai")
                .register(meterRegistry);

        processingTimer = Timer.builder("iso8583.inbound.processing")
                .description("Thời gian xử lý một bản tin ISO8583 inbound")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        meterRegistry.gauge("iso8583.inbound.connections.active", activeConnections);
        meterRegistry.gauge("iso8583.inbound.messages.inflight", inFlightMessages);
        meterRegistry.gauge("iso8583.inbound.permits.available", this,
                server -> server.getAvailablePermits());
    }

    /**
     * State theo từng connection: stream ghi, lock ghi, số bản tin đang xử lý.
     */
    private static final class ConnectionContext {
        private final String remote;
        private final Socket socket;
        private final OutputStream output;
        private final ReentrantLock writeLock = new ReentrantLock();
        private final AtomicInteger inFlight = new AtomicInteger();

        private ConnectionContext(String remote, Socket socket, OutputStream output) {
            this.remote = remote;
            this.socket = socket;
            this.output = output;
        }

        /**
         * Đóng socket cưỡng chế. Đây là cách duy nhất phá vỡ một lệnh ghi đang block
         * vĩnh viễn khi peer ngừng đọc — lệnh ghi đó sẽ bung IOException.
         */
        private void closeSocket() {
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("Lỗi khi đóng socket {}: {}", remote, e.getMessage());
            }
        }

        /**
         * Chờ các bản tin của connection này xử lý xong, để không đóng stream
         * khi worker còn đang ghi response.
         */
        private void awaitDrain(long timeoutMs) {
            if (inFlight.get() == 0) {
                return;
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (inFlight.get() > 0 && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (inFlight.get() > 0) {
                log.warn("Đóng kết nối {} khi còn {} bản tin chưa xử lý xong (quá drainTimeout {}ms)",
                        remote, inFlight.get(), timeoutMs);
            }
        }
    }
}
