package msb.com.vn.dsign.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.model.IntegrationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * Global exception handler for the D-Sign module.
 * Maps domain exceptions to the appropriate HTTP status codes
 * and wraps errors in the standard IntegrationResponse envelope.
 */
@ControllerAdvice(basePackages = "msb.com.vn.dsign")
@Slf4j
public class DSignExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", details);
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "400",
                "Validation failed: " + details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        String details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("Constraint violation: {}", details);
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "400",
                "Validation failed: " + details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(UnsupportedAlgorithmException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleUnsupportedAlgorithm(
            UnsupportedAlgorithmException ex, WebRequest request) {
        log.warn("Unsupported algorithm requested: '{}'. Supported: {}",
                ex.getUnsupportedAlgorithm(), ex.getSupportedAlgorithms());
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "400",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(CertificateInvalidException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleCertificateInvalid(
            CertificateInvalidException ex, WebRequest request) {
        log.warn("Certificate invalid — reason: {}", ex.getReason());
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "400",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(UnknownCaProviderException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleUnknownCaProvider(
            UnknownCaProviderException ex, WebRequest request) {
        log.warn("Unknown CA provider referenced: '{}'", ex.getProviderName());
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "400",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(CertificateNotFoundException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleCertificateNotFound(
            CertificateNotFoundException ex, WebRequest request) {
        log.warn("Certificate not found: '{}'", ex.getCertificateReference());
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "404",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(CaProviderException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleCaProviderError(
            CaProviderException ex, WebRequest request) {
        log.error("CA provider '{}' returned error [{}]: {}",
                ex.getProviderName(), ex.getErrorCode(), ex.getErrorDescription());
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "502",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(HsmUnavailableException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleHsmUnavailable(
            HsmUnavailableException ex, WebRequest request) {
        log.error("HSM unavailable — slot: '{}', message: {}",
                ex.getSlotIdentifier(), ex.getMessage());
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "503",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(DSignException.class)
    public ResponseEntity<IntegrationResponse<Void>> handleDSignException(
            DSignException ex, WebRequest request) {
        log.error("Unexpected D-Sign error: {}", ex.getMessage(), ex);
        IntegrationResponse<Void> response = IntegrationResponse.error(
                extractCorrelationId(request),
                "500",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Extract correlation ID from request header if available.
     */
    private String extractCorrelationId(WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        return correlationId != null ? correlationId : "unknown";
    }
}
