package msb.com.vn.qrservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GenerateVietQrRequest {

    @NotBlank(message = "Mã BIN ngân hàng không được để trống")
    private String bankBin;

    @NotBlank(message = "Số tài khoản không được để trống")
    private String bankAccount;

    private String accountName;

    @Positive(message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    private String description;
    private String transactionRef;
    private Integer width;
    private Integer height;
    private String createdBy;
}
