package msb.com.vn.qrservice.iso8583.inbound;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.config.Iso8583PackagerProvider;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Điều phối bản tin đến tới handler phù hợp theo MTI (Chain of Responsibility).
 *
 * <p>Nếu không handler nào nhận, trả về response với response code cấu hình sẵn
 * cho lỗi "unsupported message" thay vì để connection treo.</p>
 */
@Slf4j
@Component
public class Iso8583HandlerDispatcher {

    /** Response code 12 = Invalid transaction */
    private static final String RC_INVALID_TRANSACTION = "12";
    /** Response code 96 = System malfunction */
    private static final String RC_SYSTEM_ERROR = "96";

    /** Field echo lại trong response để bên gọi correlate được giao dịch */
    private static final int[] ECHO_FIELDS = {2, 3, 4, 7, 11, 12, 13, 32, 37, 41, 42, 49};

    private final List<Iso8583MessageHandler> handlers;
    private final Iso8583PackagerProvider packagerProvider;

    public Iso8583HandlerDispatcher(List<Iso8583MessageHandler> handlers,
                                    Iso8583PackagerProvider packagerProvider) {
        this.handlers = handlers;
        this.packagerProvider = packagerProvider;
    }

    @PostConstruct
    void logRegisteredHandlers() {
        log.info("Đã đăng ký {} ISO8583 handler: {}",
                handlers.size(),
                handlers.stream().map(Iso8583MessageHandler::getName).toList());
    }

    /**
     * Tìm handler theo MTI và xử lý bản tin.
     *
     * @return response để gửi lại, hoặc null nếu không cần response
     */
    public ISOMsg dispatch(Iso8583Exchange exchange) {
        String mti = exchange.getMti();
        String correlationId = exchange.getCorrelationId();

        if (mti == null) {
            log.warn("Bỏ qua bản tin không đọc được MTI, correlationId={}", correlationId);
            return null;
        }

        Iso8583MessageHandler handler = handlers.stream()
                .filter(h -> h.supports(mti))
                .findFirst()
                .orElse(null);

        if (handler == null) {
            log.warn("Không có handler cho MTI={}, correlationId={}", mti, correlationId);
            return buildRejectResponse(exchange.getIsoRequest(), RC_INVALID_TRANSACTION, correlationId);
        }

        try {
            log.debug("Dispatch MTI={} tới handler={}, correlationId={}",
                    mti, handler.getName(), correlationId);
            return handler.handle(exchange);
        } catch (Exception e) {
            log.error("Handler {} lỗi khi xử lý MTI={}, correlationId={}: {}",
                    handler.getName(), mti, correlationId, e.getMessage(), e);
            exchange.setErrorMessage(handler.getName() + ": " + e.getMessage());
            return buildRejectResponse(exchange.getIsoRequest(), RC_SYSTEM_ERROR, correlationId);
        }
    }

    /**
     * Dựng response từ chối: echo lại các field định danh + response code.
     *
     * <p>Dùng cho các trường hợp không đi qua handler: quá tải (backpressure),
     * quá deadline xử lý, MTI không hỗ trợ, handler lỗi.</p>
     *
     * @param responseCode response code đặt vào field 39, vd "96", "68"
     * @return response để gửi lại, hoặc null nếu không dựng được (MTI không hợp lệ)
     */
    public ISOMsg buildRejectResponse(ISOMsg request, String responseCode, String correlationId) {
        String mti = IsoMessageUtils.safeGetMti(request);
        if (mti == null) {
            log.warn("Không dựng được response từ chối vì thiếu MTI, correlationId={}", correlationId);
            return null;
        }

        try {
            ISOMsg response = packagerProvider.newMessage();
            response.setMTI(IsoMessageUtils.deriveResponseMti(mti));

            // Echo các field định danh giao dịch để bên gọi correlate được
            for (int field : ECHO_FIELDS) {
                if (request.hasField(field)) {
                    response.set(field, request.getString(field));
                }
            }
            response.set(IsoMessageUtils.FIELD_RESPONSE_CODE, responseCode);
            return response;

        } catch (Exception e) {
            log.error("Không dựng được response từ chối, correlationId={}: {}", correlationId, e.getMessage());
            return null;
        }
    }
}
