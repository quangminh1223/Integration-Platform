package msb.com.vn.integration.core.plugin;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.integration.common.enums.ProtocolType;
import msb.com.vn.integration.core.adapter.IntegrationAdapter;
import msb.com.vn.integration.core.transformer.MessageTransformer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central plugin registry for all platform components.
 * Implements Plugin Architecture — components self-register on startup,
 * and the engine looks them up at runtime by name or protocol.
 *
 * <p>Supports hot-reload: new adapters/transformers can be registered
 * at runtime without restart.</p>
 */
@Slf4j
@Component
public class PluginRegistry {

    private final Map<String, IntegrationAdapter> adaptersByName = new ConcurrentHashMap<>();
    private final Map<ProtocolType, IntegrationAdapter> adaptersByProtocol = new ConcurrentHashMap<>();
    private final Map<String, MessageTransformer> transformersByName = new ConcurrentHashMap<>();

    private final List<IntegrationAdapter> adapters;
    private final List<MessageTransformer> transformers;

    public PluginRegistry(List<IntegrationAdapter> adapters, List<MessageTransformer> transformers) {
        this.adapters = adapters;
        this.transformers = transformers;
    }

    @PostConstruct
    public void init() {
        adapters.forEach(this::registerAdapter);
        transformers.forEach(this::registerTransformer);
        log.info("Plugin registry initialized: {} adapters, {} transformers",
                adaptersByName.size(), transformersByName.size());
    }

    public void registerAdapter(IntegrationAdapter adapter) {
        adaptersByName.put(adapter.getName(), adapter);
        adaptersByProtocol.put(adapter.getProtocol(), adapter);
        log.info("Registered adapter: name={}, protocol={}", adapter.getName(), adapter.getProtocol());
    }

    public void registerTransformer(MessageTransformer transformer) {
        transformersByName.put(transformer.getName(), transformer);
        log.info("Registered transformer: name={}", transformer.getName());
    }

    public Optional<IntegrationAdapter> getAdapter(String name) {
        return Optional.ofNullable(adaptersByName.get(name));
    }

    public Optional<IntegrationAdapter> getAdapterByProtocol(ProtocolType protocol) {
        return Optional.ofNullable(adaptersByProtocol.get(protocol));
    }

    public Optional<MessageTransformer> getTransformer(String name) {
        return Optional.ofNullable(transformersByName.get(name));
    }

    public Map<String, IntegrationAdapter> getAllAdapters() {
        return Map.copyOf(adaptersByName);
    }

    public Map<String, MessageTransformer> getAllTransformers() {
        return Map.copyOf(transformersByName);
    }
}
