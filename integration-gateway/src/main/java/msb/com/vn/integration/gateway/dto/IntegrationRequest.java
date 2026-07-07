package msb.com.vn.integration.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Inbound integration request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationRequest {

    @NotBlank(message = "flowId is required")
    private String flowId;

    private String source;
    private String target;
    private String contentType;
    private Object payload;
    private Map<String, String> headers;
}
