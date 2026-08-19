package msb.com.vn.qrservice.mapping.declarative;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parse file transform theo cú pháp rút gọn kiểu ESQL (IBM ACE/IIB), chỉ hỗ trợ dạng gán đơn giản:
 *
 * <pre>
 * OutputRoot.JSON.Data.body.glAccount = InputRoot.JSON.Data.Body.chargeCollection.glAccount;
 * OutputRoot.JSON.Data.body.name      = UPPER(TRIM(InputRoot.JSON.Data.Body.name));
 * OutputRoot.JSON.Data.body.currency  = 'VND';
 * OutputRoot.JSON.Data.body.retryFlag = InputRoot.JSON.Data.flag; -- required
 * -- dòng comment độc lập bị bỏ qua
 * </pre>
 *
 * <h3>Quy tắc cú pháp được hỗ trợ</h3>
 * <ul>
 *   <li>Mỗi statement kết thúc bằng {@code ;}, có thể trải nhiều dòng vật lý</li>
 *   <li>Vế trái PHẢI bắt đầu {@code OutputRoot.JSON.Data.} — domain khác chưa hỗ trợ</li>
 *   <li>Vế phải là field {@code InputRoot.JSON.Data....}, literal, hoặc gọi hàm — xem
 *       {@link JsonToJsonExpressionParser} cho ngữ pháp chi tiết và danh sách hàm hỗ trợ</li>
 *   <li>Comment {@code --} tới cuối dòng bị loại trước khi parse, TRỪ khi dòng đó
 *       (sau khi loại comment) chứa từ khoá {@code required} — dùng để đánh dấu field bắt buộc</li>
 * </ul>
 *
 * <p>KHÔNG hỗ trợ: {@code IF/THEN/ELSE}, vòng lặp, domain {@code XMLNSC}/{@code MRM}/{@code BLOB}.
 * Đây là tập con có chủ đích — đủ cho field-to-field mapping có xử lý chuỗi, không phải một
 * ESQL interpreter đầy đủ. Statement dùng cú pháp ngoài tập này ném
 * {@link JsonToJsonSyntaxException} rõ ràng ngay lúc nạp file.</p>
 */
@Component
@RequiredArgsConstructor
class JsonToJsonScriptParser {

    private static final String OUTPUT_PREFIX = "OutputRoot.JSON.Data.";
    private static final String REQUIRED_MARKER = "required";

    private final JsonToJsonExpressionParser expressionParser;

    /**
     * Parse toàn bộ nội dung file thành danh sách statement.
     *
     * @throws JsonToJsonSyntaxException nếu có statement không khớp cú pháp hỗ trợ,
     *                                    kèm số dòng vật lý để dễ sửa file
     */
    List<JsonToJsonAssignment> parse(String content) {
        List<JsonToJsonAssignment> assignments = new ArrayList<>();

        for (RawStatement raw : splitStatements(content)) {
            String statement = raw.text.trim();
            if (statement.isEmpty()) {
                continue;
            }
            assignments.add(parseAssignment(statement, raw.lineNumber, raw.requiredMarked));
        }

        return assignments;
    }

    // ─── Tách file thành từng statement theo dấu ';', loại comment ──────────

    private List<RawStatement> splitStatements(String content) {
        List<RawStatement> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int startLine = 1;
        int lineNumber = 1;
        boolean requiredMarked = false;

        String[] physicalLines = content.split("\n", -1);

        for (String physicalLine : physicalLines) {
            String withoutComment = stripComment(physicalLine);
            if (withoutComment.length() < physicalLine.length()
                    && physicalLine.toLowerCase().contains(REQUIRED_MARKER)) {
                requiredMarked = true;
            }

            if (current.isEmpty() && withoutComment.isBlank()) {
                lineNumber++;
                continue;
            }
            if (current.isEmpty()) {
                startLine = lineNumber;
            }

            current.append(withoutComment).append(' ');

            if (withoutComment.contains(";")) {
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
            throw new JsonToJsonSyntaxException(startLine,
                    "Statement không kết thúc bằng ';': " + current.toString().trim());
        }

        return statements;
    }

    private String stripComment(String line) {
        int idx = line.indexOf("--");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    // ─── Parse một statement đơn ──────────────────────────────────────────────

    private JsonToJsonAssignment parseAssignment(String statement, int lineNumber, boolean required) {
        int eqIdx = findTopLevelEquals(statement, lineNumber);
        String left = statement.substring(0, eqIdx).trim();
        String right = statement.substring(eqIdx + 1).trim();

        if (!left.startsWith(OUTPUT_PREFIX)) {
            throw new JsonToJsonSyntaxException(lineNumber,
                    "Vế trái phải bắt đầu bằng '" + OUTPUT_PREFIX + "', nhận được: " + left);
        }
        String targetPath = left.substring(OUTPUT_PREFIX.length());
        if (targetPath.isBlank()) {
            throw new JsonToJsonSyntaxException(lineNumber, "Vế trái thiếu đường dẫn field sau prefix");
        }

        JsonToJsonExpression expression = expressionParser.parse(right, lineNumber);
        return new JsonToJsonAssignment(targetPath, expression, required, statement.trim());
    }

    private int findTopLevelEquals(String statement, int lineNumber) {
        int idx = statement.indexOf('=');
        if (idx < 0) {
            throw new JsonToJsonSyntaxException(lineNumber, "Thiếu dấu '=' trong statement: " + statement);
        }
        return idx;
    }

    private record RawStatement(String text, int lineNumber, boolean requiredMarked) {
    }
}
