package msb.com.vn.integration.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.core.flow.FlowEngine;
import msb.com.vn.integration.core.flow.FlowStep;
import msb.com.vn.integration.core.plugin.PluginRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway service — orchestrates the full integration flow.
 * Resolves flow definition, builds step chain, and delegates to FlowEngine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {

    private final FlowEngine flowEngine;
    private final PluginRegistry pluginRegistry;
    private final Map<String, List<FlowStep>> flowDefinitions = new ConcurrentHashMap<>();

    /**
     * Process an integration message through its configured flow.
     */
    public IntegrationMessage process(IntegrationMessage message) {
        String flowId = message.getFlowId();

        List<FlowStep> steps = flowDefinitions.get(flowId);
        if (steps == null || steps.isEmpty()) {
            log.warn("No flow definition found for flowId={}, using default pipeline", flowId);
            steps = getDefaultPipeline();
        }

        return flowEngine.execute(flowId, steps, message);
    }

    /**
     * Register a flow definition (supports hot configuration).
     */
    public void registerFlow(String flowId, List<FlowStep> steps) {
        flowDefinitions.put(flowId, steps);
        log.info("Flow registered: flowId={}, steps={}", flowId, steps.size());
    }

    public Object getAvailableFlows() {
        return Map.of(
                "flows", flowDefinitions.keySet(),
                "adapters", pluginRegistry.getAllAdapters().keySet(),
                "transformers", pluginRegistry.getAllTransformers().keySet()
        );
    }

    private List<FlowStep> getDefaultPipeline() {
        // Default minimal pipeline: validate → route → dispatch
        return List.of();
    }
}
