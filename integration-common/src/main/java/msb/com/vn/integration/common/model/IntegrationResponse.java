package msb.com.vn.integration.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard response wrapper for all integration operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationResponse<T> {

    private String correlationId;
    private boolean success;
    private String code;
    private String message;
    private T data;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> IntegrationResponse<T> success(String correlationId, T data) {
        return IntegrationResponse.<T>builder()
                .correlationId(correlationId)
                .success(true)
                .code("200")
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> IntegrationResponse<T> error(String correlationId, String code, String message) {
        return IntegrationResponse.<T>builder()
                .correlationId(correlationId)
                .success(false)
                .code(code)
                .message(message)
                .build();
    }
}
