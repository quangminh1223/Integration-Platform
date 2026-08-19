package msb.com.vn.qrservice.mapping.declarative;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import msb.com.vn.qrservice.mapping.declarative.JsonToJsonLexer.Token;
import msb.com.vn.qrservice.mapping.declarative.JsonToJsonLexer.TokenType;
import msb.com.vn.qrservice.mapping.declarative.function.TransformFunctionRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser đệ quy (recursive-descent) cho vế phải của statement {@code .esql}, hỗ trợ lồng hàm
 * tuỳ ý độ sâu: {@code UPPER(TRIM(InputRoot.JSON.Data.Body.name))}.
 *
 * <h3>Ngữ pháp hỗ trợ</h3>
 * <pre>
 * expression  := fieldRef | literal | functionCall
 * fieldRef    := "InputRoot" "." "JSON" "." "Data" ( "." IDENT )+
 * literal     := STRING | NUMBER | "TRUE" | "FALSE" | "NULL"
 * functionCall:= IDENT "(" expression ( "," expression )* ")"
 * </pre>
 *
 * <p>Validate tên hàm và số lượng tham số ngay tại đây, LÚC PARSE — không đợi tới lúc có
 * request thật mới phát hiện hàm không tồn tại hoặc gọi sai số tham số. Đây là nguyên tắc
 * xuyên suốt cả bộ mapping: lỗi cấu hình phải lộ ra khi nạp file, không phải khi chạy.</p>
 */
@Component
@RequiredArgsConstructor
class JsonToJsonExpressionParser {

    private static final String INPUT_PREFIX = "InputRoot.JSON.Data.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransformFunctionRegistry functionRegistry;

    /**
     * Parse toàn bộ vế phải (đã trim) thành cây biểu thức.
     *
     * @throws JsonToJsonSyntaxException nếu cú pháp sai, hàm không tồn tại, hoặc sai số tham số
     */
    JsonToJsonExpression parse(String rightHandSide, int lineNumber) {
        List<Token> tokens = JsonToJsonLexer.tokenize(rightHandSide, lineNumber);
        ParserState state = new ParserState(tokens, lineNumber);
        JsonToJsonExpression expr = parseExpression(state);

        if (state.peek().type() != TokenType.EOF) {
            throw new JsonToJsonSyntaxException(lineNumber,
                    "Dư token sau khi parse biểu thức: '" + state.peek().text() + "'");
        }
        return expr;
    }

    private JsonToJsonExpression parseExpression(ParserState state) {
        Token first = state.peek();

        if (first.type() == TokenType.STRING) {
            state.advance();
            return new JsonToJsonExpression.Literal(MAPPER.getNodeFactory().textNode(first.text()));
        }

        if (first.type() == TokenType.NUMBER) {
            state.advance();
            String num = first.text();
            return new JsonToJsonExpression.Literal(num.contains(".")
                    ? MAPPER.getNodeFactory().numberNode(Double.parseDouble(num))
                    : MAPPER.getNodeFactory().numberNode(Long.parseLong(num)));
        }

        if (first.type() == TokenType.IDENT) {
            return parseIdentStarted(state);
        }

        throw new JsonToJsonSyntaxException(state.lineNumber,
                "Biểu thức không hợp lệ, token không mong đợi: '" + first.text() + "'");
    }

    /**
     * Một biểu thức bắt đầu bằng IDENT có thể là: literal đặc biệt (TRUE/FALSE/NULL),
     * field reference (InputRoot.JSON.Data...), hoặc gọi hàm (TÊN_HÀM(...)).
     */
    private JsonToJsonExpression parseIdentStarted(ParserState state) {
        String ident = state.peek().text();

        if (ident.equalsIgnoreCase("TRUE")) {
            state.advance();
            return new JsonToJsonExpression.Literal(MAPPER.getNodeFactory().booleanNode(true));
        }
        if (ident.equalsIgnoreCase("FALSE")) {
            state.advance();
            return new JsonToJsonExpression.Literal(MAPPER.getNodeFactory().booleanNode(false));
        }
        if (ident.equalsIgnoreCase("NULL")) {
            state.advance();
            return new JsonToJsonExpression.Literal(MAPPER.getNodeFactory().nullNode());
        }

        // Nhìn token kế tiếp để phân biệt "InputRoot.JSON.Data..." với "TÊN_HÀM(...)"
        Token next = state.peekAt(1);
        if (next.type() == TokenType.LPAREN) {
            return parseFunctionCall(state);
        }
        if (next.type() == TokenType.DOT) {
            return parseFieldRef(state);
        }

        throw new JsonToJsonSyntaxException(state.lineNumber,
                "Không nhận diện được '" + ident + "' — phải là field InputRoot.JSON.Data.*, "
                        + "lời gọi hàm, hoặc literal TRUE/FALSE/NULL");
    }

    private JsonToJsonExpression parseFieldRef(ParserState state) {
        StringBuilder full = new StringBuilder();
        full.append(state.advance().text());
        while (state.peek().type() == TokenType.DOT) {
            state.advance();
            full.append('.').append(expectIdent(state).text());
        }

        String path = full.toString();
        if (!path.startsWith(INPUT_PREFIX)) {
            throw new JsonToJsonSyntaxException(state.lineNumber,
                    "Field reference phải bắt đầu bằng '" + INPUT_PREFIX + "', nhận được: " + path);
        }
        String fieldPath = path.substring(INPUT_PREFIX.length());
        if (fieldPath.isBlank()) {
            throw new JsonToJsonSyntaxException(state.lineNumber, "Thiếu đường dẫn field sau prefix");
        }
        return new JsonToJsonExpression.FieldRef(fieldPath);
    }

    private JsonToJsonExpression parseFunctionCall(ParserState state) {
        String functionName = expectIdent(state).text();
        expect(state, TokenType.LPAREN);

        List<JsonToJsonExpression> args = new ArrayList<>();
        if (state.peek().type() != TokenType.RPAREN) {
            args.add(parseExpression(state));
            while (state.peek().type() == TokenType.COMMA) {
                state.advance();
                args.add(parseExpression(state));
            }
        }
        expect(state, TokenType.RPAREN);

        validateFunctionCall(functionName, args.size(), state.lineNumber);
        return new JsonToJsonExpression.FunctionCall(functionName.toUpperCase(), args);
    }

    private void validateFunctionCall(String functionName, int argCount, int lineNumber) {
        var function = functionRegistry.find(functionName)
                .orElseThrow(() -> new JsonToJsonSyntaxException(lineNumber,
                        "Hàm không tồn tại: '" + functionName + "'. Kiểm tra chính tả hoặc "
                                + "đăng ký thêm TransformFunction mới."));

        int expected = function.argCount();
        int min = function.minArgCount();

        if (expected >= 0 && argCount != expected) {
            throw new JsonToJsonSyntaxException(lineNumber,
                    functionName + " cần đúng " + expected + " tham số, nhận được " + argCount);
        }
        if (expected < 0 && argCount < min) {
            throw new JsonToJsonSyntaxException(lineNumber,
                    functionName + " cần tối thiểu " + min + " tham số, nhận được " + argCount);
        }
    }

    // ─── Helpers duyệt token ─────────────────────────────────────────────────

    private Token expectIdent(ParserState state) {
        Token token = state.peek();
        if (token.type() != TokenType.IDENT) {
            throw new JsonToJsonSyntaxException(state.lineNumber,
                    "Mong đợi tên field/hàm, nhận được: '" + token.text() + "'");
        }
        return state.advance();
    }

    private void expect(ParserState state, TokenType type) {
        Token token = state.peek();
        if (token.type() != type) {
            throw new JsonToJsonSyntaxException(state.lineNumber,
                    "Mong đợi '" + type + "', nhận được: '" + token.text() + "'");
        }
        state.advance();
    }

    /** State duyệt token, giữ vị trí hiện tại + số dòng để báo lỗi. */
    private static final class ParserState {
        private final List<Token> tokens;
        private final int lineNumber;
        private int pos = 0;

        private ParserState(List<Token> tokens, int lineNumber) {
            this.tokens = tokens;
            this.lineNumber = lineNumber;
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private Token peekAt(int offset) {
            int idx = pos + offset;
            return idx < tokens.size() ? tokens.get(idx) : tokens.get(tokens.size() - 1);
        }

        private Token advance() {
            Token current = tokens.get(pos);
            if (pos < tokens.size() - 1) {
                pos++;
            }
            return current;
        }
    }
}
