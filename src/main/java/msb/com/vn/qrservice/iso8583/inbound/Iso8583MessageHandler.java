package msb.com.vn.qrservice.iso8583.inbound;

import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import org.jpos.iso.ISOMsg;

/**
 * Strategy xử lý bản tin ISO8583 đến — bước BUSINESS FLOW của pipeline.
 *
 * <p>Thêm handler mới = tạo một class implement interface này và đánh {@code @Component}.
 * {@link Iso8583HandlerDispatcher} tự phát hiện qua Spring DI, không cần sửa code hiện có.</p>
 *
 * <p>Handler nhận {@link Iso8583Exchange} nên đọc được cả bản tin ISO gốc
 * ({@code getIsoRequest()}) và bản đã convert sang JSON ({@code getJsonRequest()}).
 * Nghiệp vụ nên đọc từ JSON để không phụ thuộc số field ISO.</p>
 */
public interface Iso8583MessageHandler {

    /**
     * Tên handler dùng để log và giám sát.
     */
    String getName();

    /**
     * Handler này có xử lý MTI đó không.
     *
     * @param mti MTI của bản tin đến, vd "0200", "0800"
     */
    boolean supports(String mti);

    /**
     * Xử lý bản tin và trả về bản tin response.
     *
     * @param exchange context chứa bản tin ISO, bản JSON đã convert, correlationId
     * @return bản tin response để gửi lại cho bên gọi, hoặc {@code null} nếu không cần response
     */
    ISOMsg handle(Iso8583Exchange exchange) throws Exception;
}
