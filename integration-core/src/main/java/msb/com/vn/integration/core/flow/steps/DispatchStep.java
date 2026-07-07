package msb.com.vn.integration.core.flow.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.exception.AdapterException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import msb.com.vn.integration.core.flow.FlowContext;
import msb.com.vn.integration.core.flow.FlowStep;
import msb.com.vn.integration.core.plugin.PluginRegistry;
import msb.com.vn.integration.router.RouteDefinition;
import org.springframework.stereotype.Component;

/**
 * Built-in dispatch step — sends message to the resolved adapter.
 * This is the final step that communicates with external systems.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchStep implements FlowStep {

    private final PluginRegistry pluginRegistry;

    @Override
    public String getStepName() {
        return "dispatch";
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        RouteDefinition route = context.getVariable("resolvedRoute");
        String adapterName = context.getVariable("targetAdapter");

        if (adapterName == null || route == null) {
            throw new AdapterException("unknown", "No route resolved before dispatch step",
                    message.getCorrelationId(), false);
        }

        IntegrationAdapter adapter = pluginRegistry.getAdapter(adapterName)
                .or(() -> pluginRegistry.getAdapterByProtocol(route.getProtocol()))
                .orElseThrow(() -> new AdapterException(adapterName,
                        "Adapter not found: " + adapterName,
                        message.getCorrelationId(), false));

        log.info("Dispatching message: adapter={}, endpoint={}, correlationId={}",
                adapter.getName(), route.getEndpoint(), message.getCorrelationId());

        message.setStatus(MessageStatus.DISPATCHING);

        IntegrationMessage response = adapter.send(message);
        response.setStatus(MessageStatus.DELIVERED);

        return response;
    }

    @Override
    public boolean shouldSkip(IntegrationMessage message, FlowContext context) {
        return context.getVariable("resolvedRoute") == null;
    }
}
