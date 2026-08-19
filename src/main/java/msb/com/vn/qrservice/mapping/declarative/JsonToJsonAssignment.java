package msb.com.vn.qrservice.mapping.declarative;

/**
 * Một dòng gán đã parse, kiểu ESQL (IBM ACE/IIB):
 * <pre>
 * OutputRoot.JSON.Data.body.glAccount = InputRoot.JSON.Data.Body.chargeCollection.glAccount;
 * OutputRoot.JSON.Data.body.name      = UPPER(TRIM(InputRoot.JSON.Data.Body.name));
 * </pre>
 *
 * @param targetPath   dot-path trong output JSON (đã bỏ tiền tố {@code OutputRoot.JSON.Data.})
 * @param expression   cây biểu thức vế phải — field, literal, hoặc lời gọi hàm lồng nhau
 * @param required     true nếu dòng có comment cuối chứa "required" — kết quả null thì
 *                      ném lỗi thay vì bỏ qua lặng lẽ
 * @param rawStatement nội dung dòng gốc, dùng để log/debug khi có lỗi
 */
public record JsonToJsonAssignment(
        String targetPath,
        JsonToJsonExpression expression,
        boolean required,
        String rawStatement
) {
}
