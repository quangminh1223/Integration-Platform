package msb.com.vn.qrservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request chuyển đổi thông tin giao dịch chuyển khoản sang bản tin ISO8583")
public class TransferToIso8583Request {

    @NotBlank(message = "creditAccount không được để trống")
    @Schema(description = "Số tài khoản thụ hưởng (credit)", example = "80000002233")
    private String creditAccount;

    @NotBlank(message = "creditAmount không được để trống")
    @Schema(description = "Số tiền thụ hưởng", example = "98000")
    private String creditAmount;

    @Schema(description = "Loại tiền thụ hưởng", example = "VND", defaultValue = "VND")
    private String creditCurrency = "VND";

    @Schema(description = "Tỷ giá thụ hưởng", example = "10000000")
    private String creditRate;

    @NotBlank(message = "debitAccount không được để trống")
    @Schema(description = "Số tài khoản ghi nợ (debit)", example = "VND1217000011000")
    private String debitAccount;

    @NotBlank(message = "debitAmount không được để trống")
    @Schema(description = "Số tiền ghi nợ", example = "98000")
    private String debitAmount;

    @Schema(description = "Loại tiền ghi nợ", example = "VND", defaultValue = "VND")
    private String debitCurrency = "VND";

    @Schema(description = "Tỷ giá ghi nợ", example = "10000000")
    private String debitRate;

    @Schema(description = "Nội dung chuyển khoản (tối đa 40 ký tự)", example = "-704869-test timeout esb ok")
    private String description;

    @Schema(description = "Phí VAT", example = "0", defaultValue = "0")
    private String vatFee = "0";

    @Schema(description = "Phí dịch vụ", example = "0", defaultValue = "0")
    private String serviceFee = "0";

    @Schema(description = "Phí chủ thẻ chịu", example = "")
    private String feeOwn = "";

    @Schema(description = "Người thực hiện request", example = "user01")
    private String requestedBy;
}
