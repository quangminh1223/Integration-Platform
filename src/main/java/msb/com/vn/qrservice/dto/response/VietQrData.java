package msb.com.vn.qrservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class VietQrData {

    private String bankBin;
    private String bankName;
    private String bankAccount;
    private String accountName;
    private BigDecimal amount;
    private String description;
    private String transactionRef;
    private String serviceCode;
    private String countryCode;
    private String currency;
}
