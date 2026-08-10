package msb.com.vn.qrservice.iso8583.transform;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.qrservice.iso8583.util.IsoFieldDictionary;
import msb.com.vn.qrservice.iso8583.util.IsoMessageUtils;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bước TRANSFORMATION: chuyển bản tin ISO8583 sang JSON và ngược lại.
 *
 * <h3>Cấu trúc JSON sinh ra</h3>
 * <pre>
 * {
 *   "mti": "0200",
 *   "mtiDescription": "Financial Transaction Request",
 *   "data": {                       ← khóa camelCase, business flow dùng cái này
 *     "processingCode": "400000",
 *     "amount": "000000500000",
 *     "stan": "000123",
 *     "terminalId": "TERM0001"
 *   },
 *   "fields": {                     ← giữ nguyên số field để truy vết / debug
 *     "3": "400000",
 *     "4": "000000500000"
 *   },
 *   "fieldNames": {                 ← tên đầy đủ, tuỳ chọn
 *     "3": "Processing Code"
 *   }
 * }
 * </pre>
 *
 * <p>Stateless, thread-safe.</p>
 */
@Slf4j
@Component
public class Iso8583JsonConverter {

    public static final String KEY_MTI = "mti";
    public static final String KEY_MTI_DESCRIPTION = "mtiDescription";
    public static final String KEY_DATA = "data";
    public static final String KEY_FIELDS = "fields";
    public static final String KEY_FIELD_NAMES = "fieldNames";

    /**
     * ISO8583 → JSON (dạng Map, sẵn sàng serialize bằng Jackson).
     *
     * @param includeFieldNames có kèm tên đầy đủ của field hay không
     */
    public Map<String, Object> toJson(ISOMsg msg, boolean includeFieldNames) {
        String mti = IsoMessageUtils.safeGetMti(msg);

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, String> fieldNames = includeFieldNames ? new LinkedHashMap<>() : null;

        for (int i = 2; i <= 128; i++) {
            if (!msg.hasField(i)) {
                continue;
            }
            String value = msg.getString(i);
            if (value == null) {
                continue;
            }
            fields.put(String.valueOf(i), value);
            data.put(IsoFieldDictionary.jsonKey(i), value);
            if (fieldNames != null) {
                fieldNames.put(String.valueOf(i), IsoFieldDictionary.fieldName(i));
            }
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put(KEY_MTI, mti);
        json.put(KEY_MTI_DESCRIPTION, IsoFieldDictionary.mtiDescription(mti));
        json.put(KEY_DATA, data);
        json.put(KEY_FIELDS, fields);
        if (fieldNames != null) {
            json.put(KEY_FIELD_NAMES, fieldNames);
        }
        return json;
    }

    public Map<String, Object> toJson(ISOMsg msg) {
        return toJson(msg, false);
    }

    /**
     * JSON → ISO8583. Đọc từ khóa {@code fields} (số field) nếu có,
     * ngược lại suy ra từ {@code data} (khóa camelCase).
     *
     * @param json    map JSON theo cấu trúc do {@link #toJson} sinh ra
     * @param target  ISOMsg đã gắn packager để điền dữ liệu vào
     */
    @SuppressWarnings("unchecked")
    public ISOMsg fromJson(Map<String, Object> json, ISOMsg target) throws Exception {
        Object mti = json.get(KEY_MTI);
        if (mti != null) {
            target.setMTI(String.valueOf(mti));
        }

        Object fieldsObj = json.get(KEY_FIELDS);
        if (fieldsObj instanceof Map<?, ?> rawFields) {
            for (Map.Entry<?, ?> entry : rawFields.entrySet()) {
                int fieldNumber = Integer.parseInt(String.valueOf(entry.getKey()));
                if (fieldNumber < 2 || fieldNumber > 128) {
                    log.warn("Bỏ qua field ngoài phạm vi 2-128: {}", fieldNumber);
                    continue;
                }
                target.set(fieldNumber, String.valueOf(entry.getValue()));
            }
            return target;
        }

        Object dataObj = json.get(KEY_DATA);
        if (dataObj instanceof Map<?, ?> rawData) {
            Map<String, Integer> reverse = buildReverseKeyMap();
            for (Map.Entry<?, ?> entry : rawData.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Integer fieldNumber = reverse.get(key);
                if (fieldNumber == null) {
                    log.warn("Không map được khóa JSON '{}' sang số field ISO8583", key);
                    continue;
                }
                target.set(fieldNumber, String.valueOf(entry.getValue()));
            }
        }
        return target;
    }

    private Map<String, Integer> buildReverseKeyMap() {
        Map<String, Integer> reverse = new LinkedHashMap<>();
        for (int i = 2; i <= 128; i++) {
            reverse.put(IsoFieldDictionary.jsonKey(i), i);
        }
        return reverse;
    }
}
