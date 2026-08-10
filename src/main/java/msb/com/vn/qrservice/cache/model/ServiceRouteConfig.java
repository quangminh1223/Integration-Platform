package msb.com.vn.qrservice.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bản ghi cấu hình service (bảng SPF cũ) — chuyển từ DB sang Redis cache.
 *
 * Map từ row DB:
 * <pre>
 * (SPF_ID, SPF_CODE, SPF_NAME, SPF_TYPE, SPF_PRV_ID, SPF_DOM_ID,
 *  SPF_URL, SPF_URL_2, SPF_URL_3, STATUS, TIMEOUT, SPF_UPDATE_DATE, SPF_UPDATE_USER)
 *
 * ('979','SRV979','createIssueOfBankGuarantee','F','9','4',
 *  'http://iris.msb.com.vn/MsbLendingApi/api/v1.0.0/party/create/issue/of/bank/guarantee',
 *  null, null, 1, 60, '20-AUG-24', 'vinhpd2')
 * </pre>
 *
 * <b>Bắt buộc</b> có constructor rỗng + getter/setter (Lombok lo) để Jackson
 * deserialize được khi load từ Redis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRouteConfig {

    /** SPF_ID — '979' */
    private String spfId;

    /** SPF_CODE — 'SRV979' (dùng làm key cache) */
    private String spfCode;

    /** SPF_NAME — 'createIssueOfBankGuarantee' */
    private String spfName;

    /** SPF_TYPE — 'F' */
    private String spfType;

    /** SPF_PRV_ID — '9' (provider id) */
    private String spfPrvId;

    /** SPF_DOM_ID — '4' (domain id) */
    private String spfDomId;

    /** SPF_URL — URL backend chính */
    private String spfUrl;

    /** SPF_URL_2 — URL dự phòng 2 (có thể null) */
    private String spfUrl2;

    /** SPF_URL_3 — URL dự phòng 3 (có thể null) */
    private String spfUrl3;

    /** STATUS — VARCHAR2(20). Dữ liệu mẫu là "1" nhưng cột là chuỗi. */
    private String status;

    /** TIMEOUT — VARCHAR2(20). Vd "60" (lưu dạng chuỗi trong DB). */
    private String timeout;

    /** SPF_UPDATE_DATE — '20-AUG-24' (giữ String để không lệ thuộc format) */
    private String spfUpdateDate;

    /** SPF_UPDATE_USER — 'vinhpd2' */
    private String spfUpdateUser;
}
