package msb.com.vn.qrservice.mapping.declarative;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Tách vế phải của một statement {@code .esql} thành token, phục vụ
 * {@link JsonToJsonScriptParser} dựng cây biểu thức có hỗ trợ lồng hàm.
 *
 * <p>Chỉ tokenize phần biểu thức (vế phải) — vế trái (target path) vẫn parse bằng
 * cách so khớp prefix chuỗi đơn giản như trước, vì nó không cần hỗ trợ hàm.</p>
 */
@UtilityClass
class JsonToJsonLexer {

    enum TokenType {
        IDENT, STRING, NUMBER, DOT, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, EOF
    }

    record Token(TokenType type, String text) {
    }

    static List<Token> tokenize(String expression, int lineNumberForError) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = expression.length();

        while (i < len) {
            char c = expression.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            switch (c) {
                case '.' -> { tokens.add(new Token(TokenType.DOT, ".")); i++; }
                case '(' -> { tokens.add(new Token(TokenType.LPAREN, "(")); i++; }
                case ')' -> { tokens.add(new Token(TokenType.RPAREN, ")")); i++; }
                case '[' -> { tokens.add(new Token(TokenType.LBRACKET, "[")); i++; }
                case ']' -> { tokens.add(new Token(TokenType.RBRACKET, "]")); i++; }
                case ',' -> { tokens.add(new Token(TokenType.COMMA, ",")); i++; }
                case '\'' -> {
                    StringBuilder sb = new StringBuilder();
                    i++;
                    boolean closed = false;
                    while (i < len) {
                        char ch = expression.charAt(i);
                        if (ch == '\'') {
                            // '' bên trong chuỗi = ký tự nháy đơn thật (chuẩn SQL/ESQL)
                            if (i + 1 < len && expression.charAt(i + 1) == '\'') {
                                sb.append('\'');
                                i += 2;
                                continue;
                            }
                            closed = true;
                            i++;
                            break;
                        }
                        sb.append(ch);
                        i++;
                    }
                    if (!closed) {
                        throw new JsonToJsonSyntaxException(lineNumberForError,
                                "Chuỗi literal thiếu dấu nháy đơn đóng: '" + sb + "...");
                    }
                    tokens.add(new Token(TokenType.STRING, sb.toString()));
                }
                default -> {
                    if (Character.isDigit(c)) {
                        int start = i;
                        while (i < len && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                            i++;
                        }
                        tokens.add(new Token(TokenType.NUMBER, expression.substring(start, i)));
                    } else if (Character.isLetter(c) || c == '_') {
                        int start = i;
                        while (i < len && (Character.isLetterOrDigit(expression.charAt(i))
                                || expression.charAt(i) == '_')) {
                            i++;
                        }
                        tokens.add(new Token(TokenType.IDENT, expression.substring(start, i)));
                    } else {
                        throw new JsonToJsonSyntaxException(lineNumberForError,
                                "Ký tự không hợp lệ trong biểu thức: '" + c + "'");
                    }
                }
            }
        }

        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }
}
