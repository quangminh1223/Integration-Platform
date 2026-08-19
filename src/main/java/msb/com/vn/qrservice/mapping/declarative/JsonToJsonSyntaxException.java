package msb.com.vn.qrservice.mapping.declarative;

/**
 * Lỗi cú pháp khi parse file {@code .esql} — xảy ra lúc NẠP FILE, không phải lúc xử lý request.
 *
 * <p>{@link JsonToJsonTransformRegistry} bắt exception này cho từng file, log lỗi rồi bỏ qua
 * file đó (cùng phong cách với {@code ApiMappingRegistry}), không làm sập cả ứng dụng vì
 * một file mapping viết sai.</p>
 */
public class JsonToJsonSyntaxException extends RuntimeException {

    public JsonToJsonSyntaxException(int lineNumber, String reason) {
        super("Dòng " + lineNumber + ": " + reason);
    }
}
