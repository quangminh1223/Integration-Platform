package msb.com.vn.integration.core.flow.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.common.routing.MessageRouter;
import msb.com.vn.integration.common.routing.RouteDefinition;
import msb.com.vn.integration.core.flow.FlowContext;
import msb.com.vn.integration.core.flow.FlowStep;
import org.springframework.stereotype.Component;

/**
 * Built-in routing step — resolves target adapter and endpoint.
 * Stores route info in FlowContext for the dispatch step.
 *
 * <p>Depends on the {@link MessageRouter} abstraction, not on a concrete router. Previously this
 * injected {@code ContentBasedRouter} directly, which meant swapping the routing algorithm
 * required editing core — the Strategy pattern existed in name only. Spring injects whichever
 * {@code MessageRouter} implementation is on the classpath at runtime.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoutingStep implements FlowStep {

    private final MessageRouter router;

    @Override
    public String getStepName() {
        return "routing";
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        log.debug("Resolving route: target={}, correlationId={}",
                message.getTarget(), message.getCorrelationId());

        message.setStatus(MessageStatus.ROUTING);

        RouteDefinition route = router.resolve(message);

        // Store route in context for dispatch step
        context.addVariable("resolvedRoute", route);
        context.addVariable("targetAdapter", route.getAdapterName());
        context.addVariable("targetEndpoint", route.getEndpoint());

        // Set protocol header for adapter selection
        message.addHeader("X-Protocol", route.getProtocol().name());
        message.addHeader("X-Target-Endpoint", route.getEndpoint());

        if (route.getMethod() != null) {
            message.addHeader("X-Http-Method", route.getMethod());
        }

        log.debug("Route resolved: adapter={}, endpoint={}, protocol={}",
                route.getAdapterName(), route.getEndpoint(), route.getProtocol());

        return message;
    }
}
