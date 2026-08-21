package msb.com.vn.jsontransform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;

/**
 * Toàn bộ 12 hàm chuỗi có sẵn, mỗi hàm một dòng.
 *
 * <p>Trước đây mỗi hàm là một class riêng (12 file, ~540 dòng) trong khi phần khác nhau giữa
 * chúng chỉ là một biểu thức. Gom vào một enum để sửa hay thêm hàm chỉ cần đọc đúng một file,
 * và nhìn được cả bảng hàm trong một màn hình.</p>
 *
 * <h3>Thêm hàm mới</h3>
 * <p>Thêm một hằng số vào đây theo dạng {@code TEN(soThamSoMin, soThamSoMax, args -> ...)}.
 * Dùng {@code -1} cho max nếu không giới hạn. Không cần sửa lexer, parser hay engine.</p>
 *
 * <h3>Quy ước null</h3>
 * <ul>
 *   <li>Hàm một tham số <b>lan truyền null</b>: null vào thì null ra, và field đó sẽ bị bỏ
 *       khỏi output.</li>
 *   <li>{@code LENGTH} trả {@code 0} vì kết quả là số — "không có chuỗi" nghĩa là dài 0.</li>
 *   <li>{@code CONCAT} coi null là chuỗi rỗng, {@code COALESCE} bỏ qua null. Nếu hai hàm này
 *       cũng lan truyền null thì chỉ một field tuỳ chọn bị rỗng là cả kết quả biến mất khỏi
 *       output — không phải điều người viết script mong đợi.</li>
 * </ul>
 */
public enum StringFunctions implements TransformFunction {

    /** {@code TRIM(s)} — bỏ khoảng trắng đầu/cuối. */
    TRIM(1, 1, a -> text(apply(str(a, 0), String::trim))),

    /** {@code UPPER(s)} — chuyển sang chữ hoa. */
    UPPER(1, 1, a -> text(apply(str(a, 0), String::toUpperCase))),

    /** {@code LOWER(s)} — chuyển sang chữ thường. */
    LOWER(1, 1, a -> text(apply(str(a, 0), String::toLowerCase))),

    /** {@code LENGTH(s)} — độ dài chuỗi; null trả 0. */
    LENGTH(1, 1, a -> number(str(a, 0) == null ? 0 : str(a, 0).length())),

    /** {@code LEFT(s, n)} — n ký tự đầu; chuỗi ngắn hơn n thì trả nguyên chuỗi. */
    LEFT(2, 2, a -> text(apply(str(a, 0), s -> s.substring(0, Math.min(nonNeg(a, 1), s.length()))))),

    /** {@code RIGHT(s, n)} — n ký tự cuối; chuỗi ngắn hơn n thì trả nguyên chuỗi. */
    RIGHT(2, 2, a -> text(apply(str(a, 0), s -> s.substring(Math.max(0, s.length() - nonNeg(a, 1)))))),

    /** {@code LPAD(s, len, pad)} — đệm vào đầu cho đủ len; đã đủ thì giữ nguyên, không cắt. */
    LPAD(3, 3, a -> text(apply(str(a, 0), s -> pad(s, nonNeg(a, 1), padChar(a, 2), true)))),

    /** {@code RPAD(s, len, pad)} — đệm vào cuối cho đủ len; đã đủ thì giữ nguyên, không cắt. */
    RPAD(3, 3, a -> text(apply(str(a, 0), s -> pad(s, nonNeg(a, 1), padChar(a, 2), false)))),

    /** {@code SUBSTRING(s, start, len)} — {@code start} đánh số TỪ 1 theo chuẩn SQL/ESQL. */
    SUBSTRING(3, 3, a -> text(apply(str(a, 0), s -> substring(s, num(a, 1, 1), num(a, 2, 0))))),

    /** {@code REPLACE(s, search, repl)} — so khớp chuỗi thuần, không phải regex. */
    REPLACE(3, 3, a -> text(apply(str(a, 0), s -> {
        String search = strOrEmpty(a, 1);
        return search.isEmpty() ? s : s.replace(search, strOrEmpty(a, 2));
    }))),

    /** {@code CONCAT(a, b, ...)} — ghép chuỗi, null coi như rỗng. */
    CONCAT(2, -1, a -> {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            sb.append(strOrEmpty(a, i));
        }
        return text(sb.toString());
    }),

    /** {@code COALESCE(a, b, ...)} — giá trị đầu tiên khác null VÀ khác chuỗi rỗng. */
    COALESCE(2, -1, a -> {
        for (JsonNode node : a) {
            if (node != null && !node.isNull() && !(node.isTextual() && node.asText().isEmpty())) {
                return node;
            }
        }
        return JsonNodeFactory.instance.nullNode();
    });

    /** Phần thân hàm; tách ra thành functional interface để khai được bằng lambda. */
    @FunctionalInterface
    private interface Body {
        JsonNode apply(List<JsonNode> args);
    }

    private final int minArgs;
    private final int maxArgs;
    private final Body body;

    StringFunctions(int minArgs, int maxArgs, Body body) {
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.body = body;
    }

    /**
     * Tên hàm trong script chính là tên hằng số enum.
     *
     * <p>Không đặt tên phương thức là {@code name()} vì {@code Enum.name()} là {@code final},
     * enum sẽ không implement được interface có phương thức trùng chữ ký.</p>
     */
    @Override
    public String functionName() {
        return name();
    }

    @Override
    public int minArgs() {
        return minArgs;
    }

    @Override
    public int maxArgs() {
        return maxArgs;
    }

    @Override
    public JsonNode apply(List<JsonNode> args) {
        return body.apply(args);
    }

    // ─── Helper dùng trong thân các hàm trên ─────────────────────────────────

    /** Đọc tham số thứ i thành String; trả null nếu thiếu hoặc là JSON null. */
    private static String str(List<JsonNode> args, int i) {
        if (i >= args.size()) {
            return null;
        }
        JsonNode node = args.get(i);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    /** Như {@link #str} nhưng null thành chuỗi rỗng. */
    private static String strOrEmpty(List<JsonNode> args, int i) {
        String value = str(args, i);
        return value == null ? "" : value;
    }

    private static int num(List<JsonNode> args, int i, int fallback) {
        if (i >= args.size()) {
            return fallback;
        }
        JsonNode node = args.get(i);
        return (node == null || node.isNull()) ? fallback : node.asInt();
    }

    private static int nonNeg(List<JsonNode> args, int i) {
        return Math.max(0, num(args, i, 0));
    }

    private static char padChar(List<JsonNode> args, int i) {
        String value = str(args, i);
        return (value == null || value.isEmpty()) ? ' ' : value.charAt(0);
    }

    /** Áp một phép biến đổi chuỗi, lan truyền null thay vì ném NPE. */
    private static String apply(String value, java.util.function.UnaryOperator<String> op) {
        return value == null ? null : op.apply(value);
    }

    private static String pad(String value, int targetLength, char padChar, boolean atStart) {
        if (value.length() >= targetLength) {
            return value;
        }
        String filler = String.valueOf(padChar).repeat(targetLength - value.length());
        return atStart ? filler + value : value + filler;
    }

    /** {@code start} theo chuẩn ESQL đánh số từ 1; vượt độ dài thì cắt tới hết, không ném lỗi. */
    private static String substring(String value, int start, int length) {
        int from = Math.max(0, start - 1);
        if (length < 0 || from >= value.length()) {
            return "";
        }
        return value.substring(from, Math.min(from + length, value.length()));
    }

    private static JsonNode text(String value) {
        return value == null
                ? JsonNodeFactory.instance.nullNode()
                : JsonNodeFactory.instance.textNode(value);
    }

    private static JsonNode number(int value) {
        return JsonNodeFactory.instance.numberNode(value);
    }
}
