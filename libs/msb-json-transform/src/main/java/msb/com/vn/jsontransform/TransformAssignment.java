package msb.com.vn.jsontransform;

/**
 * Một dòng gán đã parse.
 *
 * <pre>
 * OutputRoot.JSON.Data.body.glAccount = InputRoot.JSON.Data.Body.chargeCollection.glAccount;
 * OutputRoot.JSON.Data.body.name      = UPPER(TRIM(InputRoot.JSON.Data.Body.name));
 * OutputRoot.JSON.Data.body.txnRef    = InputRoot.JSON.Data.ref;   -- required
 * </pre>
 *
 * @param targetPath   dot-path trong output JSON, đã bỏ tiền tố {@code OutputRoot.JSON.Data.}
 * @param expression   cây biểu thức vế phải
 * @param required     dòng có marker {@code -- required} đứng cùng dòng với code;
 *                     kết quả null thì ném lỗi thay vì bỏ qua lặng lẽ
 * @param rawStatement nội dung statement gốc, dùng để log và báo lỗi
 * @param lineNumber   số dòng vật lý bắt đầu statement
 */
record TransformAssignment(
        String targetPath,
        TransformExpression expression,
        boolean required,
        String rawStatement,
        int lineNumber
) {
}
