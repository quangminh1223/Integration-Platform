package msb.com.vn.qrservice.qrformat.generator;

import msb.com.vn.qrservice.qrformat.model.QrFormatType;
import msb.com.vn.qrservice.qrformat.model.QrStandardDefinition;

import java.util.Map;

/**
 * Chiến lược sinh nội dung mã QR cho một {@link QrFormatType}.
 *
 * <p>Mỗi format type có một implementation riêng. {@code DynamicQrService}
 * chọn generator phù hợp dựa trên {@link #supports()} rồi gọi {@link #generate}.</p>
 */
public interface QrContentGenerator {

    /** Format type mà generator này xử lý. */
    QrFormatType supports();

    /**
     * Sinh chuỗi nội dung mã QR từ định nghĩa chuẩn + dữ liệu đầu vào.
     *
     * @param definition định nghĩa chuẩn QR
     * @param data       dữ liệu đầu vào (key field → giá trị)
     * @return chuỗi nội dung để encode thành mã QR
     */
    String generate(QrStandardDefinition definition, Map<String, Object> data);
}
