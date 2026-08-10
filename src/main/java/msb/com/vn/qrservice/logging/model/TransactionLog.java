package msb.com.vn.qrservice.logging.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Cấu trúc log giao dịch theo chuẩn ESB CMS.
 * Mỗi giao dịch gọi backend sẽ ghi 1 record log theo format này.
 */
@Data
@Builder
public class TransactionLog {

    @JsonProperty("@timestamp")
    private List<String> timestamp;

    @JsonProperty("APP_NAME")
    private String appName;

    @JsonProperty("BANK_ID")
    private String bankId;

    @JsonProperty("BANK_SHORT_NAME")
    private String bankShortName;

    @JsonProperty("IN_XML_MSG")
    private String inXmlMsg;

    @JsonProperty("LOG_ID")
    private String logId;

    @JsonProperty("MSG_ORIGINAL")
    private String msgOriginal;

    @JsonProperty("OUT_XML_MSG")
    private String outXmlMsg;

    @JsonProperty("PROCESS_DATE")
    private String processDate;

    @JsonProperty("PROCESS_TIME")
    private String processTime;

    @JsonProperty("PROVIDER_CODE")
    private String providerCode;

    @JsonProperty("RECEIVER_DATE")
    private String receiverDate;

    @JsonProperty("RECEIVER_ID")
    private String receiverId;

    @JsonProperty("RECEIVER_TIME")
    private String receiverTime;

    @JsonProperty("REQ_APP")
    private String reqApp;

    @JsonProperty("REQ_DATE")
    private String reqDate;

    @JsonProperty("REQ_ID")
    private String reqId;

    @JsonProperty("RESP_CODE")
    private String respCode;

    @JsonProperty("RESP_DATE")
    private String respDate;

    @JsonProperty("RESP_DESC")
    private String respDesc;

    @JsonProperty("RESP_TIME")
    private String respTime;

    @JsonProperty("API_NAME")
    private String apiName;

    @JsonProperty("SPF_URL")
    private String spfUrl;

    @JsonProperty("STATUS")
    private String status;

    @JsonProperty("STEP")
    private String step;

    @JsonProperty("TIME_STAMP")
    private List<String> timeStamp;

    @JsonProperty("_index")
    private String index;

    @JsonProperty("_score")
    private Object score;
}
