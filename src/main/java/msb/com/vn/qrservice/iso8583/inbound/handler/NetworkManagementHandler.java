package msb.com.vn.qrservice.iso8583.inbound.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.config.Iso8583PackagerProvider;
import msb.com.vn.qrservice.iso8583.inbound.Iso8583MessageHandler;
import msb.com.vn.qrservice.iso8583.pipeline.Iso8583Exchange;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

/**
 * Xử lý bản tin quản trị mạng (Network Management) — MTI 0800.
 * Dùng cho echo test / sign-on / sign-off giữ kết nối.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NetworkManagementHandler implements Iso8583MessageHandler {

    private static final String MTI_NETWORK_REQUEST = "0800";
    private static final String RC_APPROVED = "00";
    private static final String DEFAULT_NMI_CODE = "301";

    private final Iso8583PackagerProvider packagerProvider;

    @Override
    public String getName() {
        return "network-management-handler";
    }

    @Override
    public boolean supports(String mti) {
        return MTI_NETWORK_REQUEST.equals(mti);
    }

    @Override
    public ISOMsg handle(Iso8583Exchange exchange) throws Exception {
        ISOMsg request = exchange.getIsoRequest();

        String nmiCode = request.hasField(IsoMessageUtils.FIELD_NMI_CODE)
                ? request.getString(IsoMessageUtils.FIELD_NMI_CODE)
                : DEFAULT_NMI_CODE;

        log.info("Network management đến: nmiCode={}, stan={}, correlationId={}",
                nmiCode, exchange.getStan(), exchange.getCorrelationId());

        ISOMsg response = packagerProvider.newMessage();
        response.setMTI(IsoMessageUtils.deriveResponseMti(MTI_NETWORK_REQUEST));

        if (request.hasField(IsoMessageUtils.FIELD_TRANSMISSION_DATETIME)) {
            response.set(IsoMessageUtils.FIELD_TRANSMISSION_DATETIME,
                    request.getString(IsoMessageUtils.FIELD_TRANSMISSION_DATETIME));
        }
        if (request.hasField(IsoMessageUtils.FIELD_STAN)) {
            response.set(IsoMessageUtils.FIELD_STAN, request.getString(IsoMessageUtils.FIELD_STAN));
        }

        IsoMessageUtils.fillTimeFields(response);
        response.set(IsoMessageUtils.FIELD_RESPONSE_CODE, RC_APPROVED);
        response.set(IsoMessageUtils.FIELD_NMI_CODE, nmiCode);

        return response;
    }
}
