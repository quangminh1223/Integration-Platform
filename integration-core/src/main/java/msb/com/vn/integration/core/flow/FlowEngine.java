package msb.com.vn.integration.core.flow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.idempotent.IdempotentStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The central orchestration engine executing integration flows.
 * Implements Chain of Responsibility — steps are executed in order,
 * each step can modify or abort the message.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Idempotent processing (duplicate detection via messageId)</li>
 *   <li>Correlation ID propagation</li>
 *   <li>Step-level audit trail</li>
 *   <li>Graceful abort handling</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowEngine {

    private final IdempotentStore idempotentStore;

    /**
     * Execute a flow (ordered list of steps) for the given message.
     */
    public IntegrationMessage execute(String flowId, List<FlowStep> steps, IntegrationMessage message) {
        // Idempotent check
        if (idempotentStore.isDuplicate(message.getMessageId())) {
            log.warn("Duplicate message detected: messageId={}, correlationId={}",
                    message.getMessageId(), message.getCorrelationId());
            message.setStatus(MessageStatus.DELIVERED);
            return message;
        }

        // Mark as processing
        idempotentStore.markProcessing(message.getMessageId());

        // Build execution context
        FlowContext context = FlowContext.builder()
                .flowId(flowId)
                .executionId(UUID.randomUUID().toString())
                .correlationId(message.getCorrelationId())
                .build();

        log.info("Starting flow execution: flowId={}, executionId={}, correlationId={}",
                flowId, context.getExecutionId(), context.getCorrelationId());

        // Sort steps by order
        List<FlowStep> orderedSteps = steps.stream()
                .sorted(Comparator.comparingInt(FlowStep::getOrder))
                .toList();

        IntegrationMessage current = message;

        for (FlowStep step : orderedSteps) {
            if (context.isAborted()) {
                log.warn("Flow aborted at step={}, reason={}", step.getStepName(), context.getAbortReason());
                current.setStatus(MessageStatus.FAILED);
                break;
            }

            if (step.shouldSkip(current, context)) {
                log.debug("Skipping step: {}", step.getStepName());
                continue;
            }

            Instant stepStart = Instant.now();
            try {
                log.debug("Executing step: {}", step.getStepName());
                current = step.execute(current, context);

                context.addAudit(FlowContext.StepAudit.builder()
                        .stepName(step.getStepName())
                        .startTime(stepStart)
                        .endTime(Instant.now())
                        .success(true)
                        .build());

            } catch (Exception e) {
                log.error("Step failed: step={}, correlationId={}, error={}",
                        step.getStepName(), context.getCorrelationId(), e.getMessage(), e);

                context.addAudit(FlowContext.StepAudit.builder()
                        .stepName(step.getStepName())
                        .startTime(stepStart)
                        .endTime(Instant.now())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());

                current.setStatus(MessageStatus.FAILED);
                idempotentStore.markFailed(message.getMessageId());
                throw e;
            }
        }

        if (current.getStatus() != MessageStatus.FAILED) {
            current.setStatus(MessageStatus.DELIVERED);
            idempotentStore.markCompleted(message.getMessageId());
        }

        log.info("Flow execution completed: flowId={}, executionId={}, status={}",
                flowId, context.getExecutionId(), current.getStatus());

        return current;
    }
}
