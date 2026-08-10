package msb.com.vn.qrservice.common.http;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * Wrapper kết quả trả về từ một REST call.
 */
@Getter
@Builder
public class RestResponse<T> {

    /** HTTP status code */
    private final HttpStatusCode statusCode;

    /** Response body đã được deserialize */
    private final T body;

    /** true nếu statusCode là 2xx */
    private final boolean success;

    /** Thông báo lỗi nếu call thất bại */
    private final String errorMessage;

    public static <T> RestResponse<T> ok(HttpStatusCode status, T body) {
        return RestResponse.<T>builder()
                .statusCode(status)
                .body(body)
                .success(true)
                .build();
    }

    public static <T> RestResponse<T> error(HttpStatusCode status, String message) {
        return RestResponse.<T>builder()
                .statusCode(status)
                .success(false)
                .errorMessage(message)
                .build();
    }
}
