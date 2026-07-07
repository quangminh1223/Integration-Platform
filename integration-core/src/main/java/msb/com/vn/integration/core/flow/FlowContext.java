package msb.com.vn.integration.core.flow;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object carried through the entire flow execution.
 * Holds state, audit trail, and shared data between steps.
 */
@Data
@Builder
public class FlowContext {

    /** Flow definition ID */
    private String flowId;

    /** Execution instance ID */
    private String executionId;

    /** Correlation ID for distributed tracing */
    private String correlationId;

    /** Start time of this flow execution */
    @Builder.Default
    private Instant startTime = Instant.now();

    /** Shared variables between steps */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /** Audit trail of executed steps */
    @Builder.Default
    private List<StepAudit> auditTrail = new ArrayList<>();

    /** Whether flow should be aborted */
    @Builder.Default
    private boolean aborted = false;

    /** Abort reason if aborted */
    private String abortReason;

    public void addVariable(String key, Object value) {
        variables.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }

    public void addAudit(StepAudit audit) {
        auditTrail.add(audit);
    }

    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
    }

    @Data
    @Builder
    public static class StepAudit {
        private String stepName;
        private Instant startTime;
        private Instant endTime;
        private boolean success;
        private String errorMessage;
    }
}
