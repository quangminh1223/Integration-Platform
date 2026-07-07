package msb.com.vn.integration.gateway;

import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.exception.IntegrationException;
import msb.com.vn.integration.common.exception.RoutingException;
import msb.com.vn.integration.common.exception.TransformationException;
import msb.com.vn.integration.common.model.IntegrationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the integration gateway.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoutingException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleRoutingException(RoutingException e) {
        log.error("Routing error: correlationId={}, message={}", e.getCorrelationId(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(IntegrationResponse.error(e.getCorrelationId(), "ROUTING_ERROR", e.getMessage()));
    }

    @ExceptionHandler(TransformationException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleTransformException(TransformationException e) {
        log.error("Transformation error: correlationId={}, message={}", e.getCorrelationId(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(IntegrationResponse.error(e.getCorrelationId(), "TRANSFORM_ERROR", e.getMessage()));
    }

    @ExceptionHandler(AdapterException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleAdapterException(AdapterException e) {
        log.error("Adapter error: adapter={}, correlationId={}, message={}",
                e.getAdapterName(), e.getCorrelationId(), e.getMessage());
        HttpStatus status = e.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(IntegrationResponse.error(e.getCorrelationId(), "ADAPTER_ERROR", e.getMessage()));
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleIntegrationException(IntegrationException e) {
        log.error("Integration error: correlationId={}, code={}, message={}",
                e.getCorrelationId(), e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IntegrationResponse.error(e.getCorrelationId(), e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(IntegrationResponse.error(null, "VALIDATION_ERROR", errorMsg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<IntegrationResponse<Void>> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IntegrationResponse.error(null, "INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
