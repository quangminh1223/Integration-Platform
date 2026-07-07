package msb.com.vn.integration.core.flow.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.exception.IntegrationException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.flow.FlowContext;
import msb.com.vn.integration.core.flow.FlowStep;
import org.springframework.stereotype.Component;

/**
 * Built-in validation step — validates message structure and required fields.
 * Can be extended with JSON Schema or custom validators.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationStep implements FlowStep {

    @Override
    public String getStepName() {
        return "validation";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        log.debug("Validating message: messageId={}, correlationId={}",
                message.getMessageId(), message.getCorrelationId());

        // Basic structural validation
        if (message.getPayload() == null) {
            throw new IntegrationException(
                    "Message payload cannot be null",
                    "VALIDATION_ERROR",
                    message.getCorrelationId(),
                    false);
        }

        if (message.getFlowId() == null || message.getFlowId().isBlank()) {
            throw new IntegrationException(
                    "FlowId is required",
                    "VALIDATION_ERROR",
                    message.getCorrelationId(),
                    false);
        }

        message.setStatus(MessageStatus.VALIDATING);
        log.debug("Validation passed: correlationId={}", message.getCorrelationId());
        return message;
    }
}
