package msb.com.vn.jsontransform;

import java.util.ArrayList;
import java.util.List;

/**
 * Parse script transform theo cú pháp rút gọn kiểu ESQL (IBM ACE/IIB), chỉ hỗ trợ dạng gán:
 *
 * <pre>
 * OutputRoot.JSON.Data.body.glAccount = InputRoot.JSON.Data.Body.chargeCollection.glAccount;
 * OutputRoot.JSON.Data.body.name      = UPPER(TRIM(InputRoot.JSON.Data.Body.name));
 * OutputRoot.JSON.Data.body.currency  = 'VND';
 * OutputRoot.JSON.Data.body.txnRef    = InputRoot.JSON.Data.ref;   -- required
 * -- dòng comment độc lập bị bỏ qua hoàn toàn
 * </pre>
 *
 * <h3>Quy tắc cú pháp</h3>
 * <ul>
 *   <li>Mỗi statement kết thúc bằng {@code ;}, có thể trải nhiều dòng vật lý</li>
 *   <li>Vế trái PHẢI bắt đầu {@code OutputRoot.JSON.Data.}</li>
 *   <li>Vế phải là field {@code InputRoot.JSON.Data....}, literal, hoặc gọi hàm —
 *       xem {@link ExpressionParser}</li>
 *   <li>Comment {@code --} tới cuối dòng bị loại trước khi parse</li>
 * </ul>
 *
 * <h3>Marker {@code required}</h3>
 * <p>Một dòng gán được coi là bắt buộc khi comment <b>trên cùng dòng với code</b> bắt đầu
 * bằng từ khoá {@code required}:</p>
 * <pre>
 * OutputRoot.JSON.Data.body.ref = InputRoot.JSON.Data.ref;  -- required
 * OutputRoot.JSON.Data.body.ref = InputRoot.JSON.Data.ref;  -- required: mã đối soát
 * </pre>
 * <p>Comment độc lập (không có code cùng dòng) KHÔNG bao giờ đánh dấu statement nào, kể cả
 * khi nội dung có chứa chữ "required". Điều này quan trọng: văn bản mô tả kiểu
 * {@code -- không field nào required} phải không có tác dụng gì, thay vì âm thầm biến
 * statement kế tiếp thành bắt buộc.</p>
 *
 * <p>KHÔNG hỗ trợ: {@code IF/THEN/ELSE}, vòng lặp, domain {@code XMLNSC}/{@code MRM}/{@code BLOB}.
 * Đây là tập con có chủ đích. Statement ngoài tập này ném
 * {@link JsonTransformSyntaxException} ngay lúc compile, kèm số dòng.</p>
 */
final class ScriptParser {

    private static final String OUTPUT_PREFIX = "OutputRoot.JSON.Data.";
    private static final String REQUIRED_MARKER = "required";

    private final ExpressionParser expressionParser;

    ScriptParser(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    List<TransformAssignment> parse(String content) {
        List<TransformAssignment> assignments = new ArrayList<>();

        for (RawStatement raw : splitStatements(content)) {
            String statement = raw.text.trim();
            if (statement.isEmpty()) {
                continue;
            }
            assignments.add(parseAssignment(statement, raw.lineNumber, raw.requiredMarked));
        }

        return assignments;
    }

    // ─── Tách script thành từng statement theo ';', loại comment ─────────────

    private List<RawStatement> splitStatements(String content) {
        List<RawStatement> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startLine = 1;
        int lineNumber = 1;
        boolean requiredMarked = false;

        for (String physicalLine : content.split("\n", -1)) {
            int commentIdx = findCommentStart(physicalLine);
            String code = commentIdx >= 0 ? physicalLine.substring(0, commentIdx) : physicalLine;
            String comment = commentIdx >= 0 ? physicalLine.substring(commentIdx + 2) : "";

            boolean lineHasCode = !code.isBlank();

            // Marker chỉ có hiệu lực khi comment nằm CÙNG DÒNG với code. Nhờ vậy comment
            // mô tả độc lập không thể vô tình đánh dấu statement phía sau.
            if (lineHasCode && isRequiredMarker(comment)) {
                requiredMarked = true;
            }

            if (current.isEmpty() && !lineHasCode) {
                lineNumber++;
                continue;
            }
            if (current.isEmpty()) {
                startLine = lineNumber;
            }

            current.append(code).append(' ');

            if (code.indexOf(';') >= 0) {
                String[] parts = current.toString().split(";", -1);
                for (int i = 0; i < parts.length - 1; i++) {
                    statements.add(new RawStatement(parts[i], startLine, requiredMarked));
                    requiredMarked = false;
                    startLine = lineNumber;
                }
                current = new StringBuilder(parts[parts.length - 1]);
            }

            lineNumber++;
        }

        if (!current.toString().isBlank()) {
            throw new JsonTransformSyntaxException(startLine,
                    "Statement không kết thúc bằng ';': " + current.toString().trim());
        }

        return statements;
    }

    /**
     * Tìm vị trí bắt đầu comment {@code --}, BỎ QUA phần nằm trong chuỗi literal.
     *
     * <p>Nhờ vậy {@code REPLACE(x, '--', '')} hoạt động đúng, thay vì bị cắt thành
     * {@code REPLACE(x, '} rồi báo lỗi cú pháp.</p>
     *
     * @return chỉ số của {@code --} đầu tiên ngoài chuỗi, hoặc {@code -1} nếu không có
     */
    private static int findCommentStart(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') {
                // '' bên trong chuỗi là ký tự nháy đơn thật, không phải kết thúc chuỗi
                if (inString && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inString = !inString;
            } else if (!inString && c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Comment có phải marker {@code required} hay không.
     *
     * <p>Yêu cầu comment BẮT ĐẦU bằng đúng từ {@code required} (không phân biệt hoa/thường),
     * theo sau là hết chuỗi hoặc một ký tự không phải chữ/số. So khớp kiểu này loại được
     * văn xuôi có chứa chữ "required" ở giữa câu.</p>
     */
    private static boolean isRequiredMarker(String comment) {
        String c = comment.trim();
        if (!c.regionMatches(true, 0, REQUIRED_MARKER, 0, REQUIRED_MARKER.length())) {
            return false;
        }
        if (c.length() == REQUIRED_MARKER.length()) {
            return true;
        }
        return !Character.isLetterOrDigit(c.charAt(REQUIRED_MARKER.length()));
    }

    // ─── Parse một statement đơn ────────────────────────────────────────────

    private TransformAssignment parseAssignment(String statement, int lineNumber, boolean required) {
        int eqIdx = findAssignmentEquals(statement, lineNumber);
        String left = statement.substring(0, eqIdx).trim();
        String right = statement.substring(eqIdx + 1).trim();

        if (!left.startsWith(OUTPUT_PREFIX)) {
            throw new JsonTransformSyntaxException(lineNumber,
                    "Vế trái phải bắt đầu bằng '" + OUTPUT_PREFIX + "', nhận được: " + left);
        }
        String targetPath = left.substring(OUTPUT_PREFIX.length());
        if (targetPath.isBlank()) {
            throw new JsonTransformSyntaxException(lineNumber,
                    "Vế trái thiếu đường dẫn field sau prefix '" + OUTPUT_PREFIX + "'");
        }
        if (right.isBlank()) {
            throw new JsonTransformSyntaxException(lineNumber, "Vế phải trống");
        }

        TransformExpression expression = expressionParser.parse(right, lineNumber);
        return new TransformAssignment(targetPath, expression, required, statement.trim(), lineNumber);
    }

    private int findAssignmentEquals(String statement, int lineNumber) {
        int idx = statement.indexOf('=');
        if (idx < 0) {
            throw new JsonTransformSyntaxException(lineNumber,
                    "Thiếu dấu '=' trong statement: " + statement);
        }
        return idx;
    }

    private record RawStatement(String text, int lineNumber, boolean requiredMarked) {
    }
}
