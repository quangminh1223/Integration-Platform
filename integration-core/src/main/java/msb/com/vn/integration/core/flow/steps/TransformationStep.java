package msb.com.vn.integration.core.flow.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.exception.TransformationException;
import msb.com.vn.integration.common.model.IntegrationMessage;
import msb.com.vn.integration.common.model.MessageStatus;
import msb.com.vn.integration.core.flow.FlowContext;
import msb.com.vn.integration.core.flow.FlowStep;
import msb.com.vn.integration.core.plugin.PluginRegistry;
import msb.com.vn.integration.core.transformer.MessageTransformer;
import org.springframework.stereotype.Component;

/**
 * Built-in transformation step — delegates to registered transformers.
 * Transformer selection based on flow configuration or message contentType.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransformationStep implements FlowStep {

    private final PluginRegistry pluginRegistry;

    @Override
    public String getStepName() {
        return "transformation";
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public IntegrationMessage execute(IntegrationMessage message, FlowContext context) {
        String transformerName = context.getVariable("transformer");
        if (transformerName == null) {
            log.debug("No transformer specified, skipping transformation");
            return message;
        }

        MessageTransformer transformer = pluginRegistry.getTransformer(transformerName)
                .orElseThrow(() -> new TransformationException(
                        "Transformer not found: " + transformerName,
                        message.getCorrelationId()));

        log.debug("Applying transformer: name={}, correlationId={}",
                transformerName, message.getCorrelationId());

        message.setStatus(MessageStatus.TRANSFORMING);
        return transformer.transform(message);
    }

    @Override
    public boolean shouldSkip(IntegrationMessage message, FlowContext context) {
        return context.getVariable("transformer") == null;
    }
}
