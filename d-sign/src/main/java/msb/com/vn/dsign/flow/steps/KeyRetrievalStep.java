package msb.com.vn.dsign.flow.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.dsign.exception.DSignException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.core.flow.FlowContext;
import msb.com.vn.integration.core.flow.FlowStep;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Flow step that extracts and validates the HSM key reference from the FlowContext.
 *
 * <p>The CertificateResolutionStep (order 200) places the {@code keyReference} value
 * in the flow context after resolving the certificate. This step validates the key
 * reference format and ensures it is available for the HsmSigningStep (order 500).</p>
 *
 * <p>Key reference format: {@code HSM-<SLOT_ID>-KEY-<KEY_ID>} where SLOT_ID is
 * alphanumeric (may contain digits) and KEY_ID is alphanumeric with optional hyphens.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KeyRetrievalStep implements FlowStep {

    private static final String STEP_NAME = "key-retrieval";
    private static final int ORDER = 300;

    /**
     * Key reference format: HSM-{slotId}-KEY-{keyId}
     * Examples: HSM-SLOT0-KEY-001, HSM-SLOT1-KEY-ABC123
     */
    private static final Pattern KEY_REFERENCE_PATTERN =
            Pattern.compile("^HSM-[A-Za-z0-9]+-KEY-[A-Za-z0-9\\-]+$");

    static final String CONTEXT_KEY_REFERENCE = "keyReference";

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        log.debug("Executing key retrieval step for correlationId={}", context.getCorrelationId());

        String keyReference = context.getVariable(CONTEXT_KEY_REFERENCE);

        if (keyReference == null || keyReference.isBlank()) {
            throw new DSignException(
                    "Key reference is missing from flow context. " +
                    "CertificateResolutionStep must set keyReference before this step executes.");
        }

        if (!KEY_REFERENCE_PATTERN.matcher(keyReference).matches()) {
            throw new DSignException(
                    String.format("Invalid key reference format: '%s'. " +
                            "Expected format: HSM-<SLOT_ID>-KEY-<KEY_ID> " +
                            "(e.g., HSM-SLOT0-KEY-001)", keyReference));
        }

        log.debug("Key reference validated successfully: {} for correlationId={}",
                keyReference, context.getCorrelationId());

        // Key reference is already in context (placed by CertificateResolutionStep).
        // This step confirms its validity so downstream HsmSigningStep can use it safely.
        return message;
    }
}
