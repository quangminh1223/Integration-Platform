package msb.com.vn.qrservice.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bản ghi phân quyền consumer ↔ service (bảng ATZ) — cache lên Redis.
 *
 * Cột bảng:
 * <pre>
 * (ATZ_ID, ATZ_CSM_ID, ATZ_SPF_ID, CSM_CREATED_DATE, CSM_UPDATED_DATE,
 *  CSM_UPDATED_USER, CSM_CREATED_USER, STATUS, ATZ_CSM_CODE, ATZ_SPF_NAME)
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationConfig {

    /** ATZ_ID — khóa chính (dùng làm key cache). */
    private String atzId;

    /** ATZ_CSM_ID — id consumer. */
    private String atzCsmId;

    /** ATZ_SPF_ID — id service (trỏ tới SPF_ID). */
    private String atzSpfId;

    /** CSM_CREATED_DATE — ngày tạo (giữ String theo format Oracle). */
    private String csmCreatedDate;

    /** CSM_UPDATED_DATE — ngày cập nhật. */
    private String csmUpdatedDate;

    /** CSM_UPDATED_USER — người cập nhật. */
    private String csmUpdatedUser;

    /** CSM_CREATED_USER — người tạo. */
    private String csmCreatedUser;

    /** STATUS — 1 = active, 0 = inactive. */
    private Integer status;

    /** ATZ_CSM_CODE — mã consumer. */
    private String atzCsmCode;

    /** ATZ_SPF_NAME — tên service được phép gọi. */
    private String atzSpfName;
}
