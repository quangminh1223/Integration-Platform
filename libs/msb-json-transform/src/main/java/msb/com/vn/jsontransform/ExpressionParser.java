package msb.com.vn.jsontransform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parse vế phải của một dòng gán thành cây biểu thức, hỗ trợ lồng hàm tuỳ ý độ sâu:
 * {@code UPPER(TRIM(InputRoot.JSON.Data.Body.name))}.
 *
 * <h3>Ngữ pháp — toàn bộ chỉ có ba dạng</h3>
 * <pre>
 * expression := fieldRef | literal | functionCall
 * fieldRef   := "InputRoot.JSON.Data" ( "." ident | "[" so "]" )+
 * literal    := 'chuoi' | so | TRUE | FALSE | NULL
 * functionCall := ident "(" expression ( "," expression )* ")"
 * </pre>
 *
 * <p>Đọc trực tiếp trên chuỗi ký tự, không qua bước tách token trung gian. Ngữ pháp nhỏ như
 * trên thì danh sách token chỉ là một tầng dữ liệu phải dựng rồi duyệt lại — bỏ đi thì luồng
 * xử lý ngắn hơn và chỉ còn một file để đọc khi cần sửa.</p>
 *
 * <p>Tên hàm và số tham số được kiểm NGAY TẠI ĐÂY, lúc compile — không đợi có request thật
 * mới phát hiện gọi sai.</p>
 */
final class ExpressionParser {

    private static final String INPUT_PREFIX = "InputRoot.JSON.Data.";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final TransformFunctionRegistry functions;

    ExpressionParser(TransformFunctionRegistry functions) {
        this.functions = functions;
    }

    TransformExpression parse(String source, int lineNumber) {
        Cursor cursor = new Cursor(source, lineNumber);
        TransformExpression expression = parseExpression(cursor);
        cursor.skipSpaces();
        if (!cursor.atEnd()) {
            throw cursor.error("Dư ký tự sau biểu thức: '" + cursor.rest() + "'");
        }
        return expression;
    }

    private TransformExpression parseExpression(Cursor cursor) {
        cursor.skipSpaces();
        if (cursor.atEnd()) {
            throw cursor.error("Thiếu biểu thức");
        }

        char c = cursor.peek();
        if (c == '\'') {
            return new TransformExpression.Literal(NODES.textNode(cursor.readQuoted()));
        }
        if (Character.isDigit(c)) {
            return new TransformExpression.Literal(cursor.readNumber());
        }
        if (Character.isLetter(c) || c == '_') {
            return parseIdentifier(cursor);
        }
        throw cursor.error("Ký tự không hợp lệ trong biểu thức: '" + c + "'");
    }

    /** Một identifier có thể mở đầu literal TRUE/FALSE/NULL, field reference, hoặc lời gọi hàm. */
    private TransformExpression parseIdentifier(Cursor cursor) {
        String ident = cursor.readIdent();

        if (ident.equalsIgnoreCase("TRUE")) {
            return new TransformExpression.Literal(NODES.booleanNode(true));
        }
        if (ident.equalsIgnoreCase("FALSE")) {
            return new TransformExpression.Literal(NODES.booleanNode(false));
        }
        if (ident.equalsIgnoreCase("NULL")) {
            return new TransformExpression.Literal(NODES.nullNode());
        }

        cursor.skipSpaces();
        if (cursor.peekIs('(')) {
            return parseFunctionCall(cursor, ident);
        }
        if (cursor.peekIs('.') || cursor.peekIs('[')) {
            return parseFieldRef(cursor, ident);
        }
        throw cursor.error("Không nhận diện được '" + ident + "' — phải là field "
                + INPUT_PREFIX + "*, lời gọi hàm, hoặc literal TRUE/FALSE/NULL");
    }

    private TransformExpression parseFieldRef(Cursor cursor, String firstSegment) {
        StringBuilder path = new StringBuilder(firstSegment);

        while (true) {
            if (cursor.peekIs('.')) {
                cursor.next();
                path.append('.').append(cursor.readIdentRequired());
            } else if (cursor.peekIs('[')) {
                cursor.next();
                path.append('[').append(cursor.readIndex()).append(']');
                cursor.expect(']');
            } else {
                break;
            }
        }

        String full = path.toString();
        if (!full.startsWith(INPUT_PREFIX)) {
            throw cursor.error("Field reference phải bắt đầu bằng '" + INPUT_PREFIX
                    + "', nhận được: " + full);
        }
        String fieldPath = full.substring(INPUT_PREFIX.length());
        if (fieldPath.isBlank()) {
            throw cursor.error("Thiếu đường dẫn field sau prefix '" + INPUT_PREFIX + "'");
        }
        return new TransformExpression.FieldRef(fieldPath);
    }

    private TransformExpression parseFunctionCall(Cursor cursor, String name) {
        cursor.expect('(');

        List<TransformExpression> args = new ArrayList<>();
        cursor.skipSpaces();
        if (!cursor.peekIs(')')) {
            args.add(parseExpression(cursor));
            cursor.skipSpaces();
            while (cursor.peekIs(',')) {
                cursor.next();
                args.add(parseExpression(cursor));
                cursor.skipSpaces();
            }
        }
        cursor.expect(')');

        checkFunction(cursor, name, args.size());
        return new TransformExpression.FunctionCall(name.toUpperCase(), args);
    }

    private void checkFunction(Cursor cursor, String name, int argCount) {
        TransformFunction function = functions.find(name)
                .orElseThrow(() -> cursor.error("Hàm không tồn tại: '" + name
                        + "'. Hàm khả dụng: " + functions.registeredNames()));

        if (argCount < function.minArgs()) {
            throw cursor.error(name + " cần tối thiểu " + function.minArgs()
                    + " tham số, nhận được " + argCount);
        }
        if (function.maxArgs() >= 0 && argCount > function.maxArgs()) {
            throw cursor.error(name + " cần tối đa " + function.maxArgs()
                    + " tham số, nhận được " + argCount);
        }
    }

    /**
     * Con trỏ đọc trên chuỗi biểu thức: giữ vị trí hiện tại và số dòng để báo lỗi.
     * Mọi thao tác đọc đều tiến con trỏ, nên không có trạng thái nào phải đồng bộ tay.
     */
    private static final class Cursor {

        private final String source;
        private final int lineNumber;
        private int pos;

        Cursor(String source, int lineNumber) {
            this.source = source;
            this.lineNumber = lineNumber;
        }

        boolean atEnd() {
            return pos >= source.length();
        }

        char peek() {
            return source.charAt(pos);
        }

        boolean peekIs(char expected) {
            return pos < source.length() && source.charAt(pos) == expected;
        }

        void next() {
            pos++;
        }

        void skipSpaces() {
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
                pos++;
            }
        }

        String rest() {
            return source.substring(pos).trim();
        }

        void expect(char expected) {
            skipSpaces();
            if (!peekIs(expected)) {
                throw error("Mong đợi '" + expected + "' nhưng "
                        + (atEnd() ? "đã hết biểu thức" : "nhận được '" + peek() + "'"));
            }
            pos++;
        }

        String readIdent() {
            int start = pos;
            while (pos < source.length()
                    && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
                pos++;
            }
            return source.substring(start, pos);
        }

        String readIdentRequired() {
            String ident = readIdent();
            if (ident.isEmpty()) {
                throw error("Mong đợi tên field sau dấu '.'");
            }
            return ident;
        }

        int readIndex() {
            int start = pos;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw error("Chỉ số mảng phải là số nguyên");
            }
            return Integer.parseInt(source.substring(start, pos));
        }

        /** Đọc chuỗi trong nháy đơn; {@code ''} bên trong là một nháy đơn thật (chuẩn SQL/ESQL). */
        String readQuoted() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (c == '\'') {
                    if (pos + 1 < source.length() && source.charAt(pos + 1) == '\'') {
                        sb.append('\'');
                        pos += 2;
                        continue;
                    }
                    pos++;
                    return sb.toString();
                }
                sb.append(c);
                pos++;
            }
            throw error("Chuỗi literal thiếu dấu nháy đơn đóng: '" + sb + "...");
        }

        JsonNode readNumber() {
            int start = pos;
            while (pos < source.length()
                    && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
                pos++;
            }
            String raw = source.substring(start, pos);
            try {
                return raw.contains(".")
                        ? NODES.numberNode(Double.parseDouble(raw))
                        : NODES.numberNode(Long.parseLong(raw));
            } catch (NumberFormatException e) {
                throw error("Số không hợp lệ: '" + raw + "'");
            }
        }

        JsonTransformSyntaxException error(String reason) {
            return new JsonTransformSyntaxException(lineNumber, reason);
        }
    }
}
