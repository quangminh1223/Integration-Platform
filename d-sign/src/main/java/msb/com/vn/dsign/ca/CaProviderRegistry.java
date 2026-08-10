package msb.com.vn.dsign.ca;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msb.com.vn.dsign.config.DSignConfig;
import msb.com.vn.dsign.exception.UnknownCaProviderException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for CA provider beans. Auto-discovers all {@link CaProvider} implementations
 * via Spring DI and provides lookup by provider name.
 *
 * <p>The active provider per environment is resolved from the {@code dsign.ca.active-provider}
 * configuration property.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaProviderRegistry {

    private final List<CaProvider> providers;
    private final DSignConfig dSignConfig;

    private final Map<String, CaProvider> providersByName = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        providers.forEach(provider -> {
            providersByName.put(provider.getProviderName(), provider);
            log.info("Registered CA provider: {}", provider.getProviderName());
        });
        log.info("CA provider registry initialized with {} providers", providersByName.size());
    }

    /**
     * Get a CA provider by name.
     *
     * @param name the provider name
     * @return the CA provider
     * @throws UnknownCaProviderException if no provider is registered with the given name
     */
    public CaProvider getProvider(String name) {
        CaProvider provider = providersByName.get(name);
        if (provider == null) {
            throw new UnknownCaProviderException(name);
        }
        return provider;
    }

    /**
     * Get the active CA provider as configured by {@code dsign.ca.active-provider}.
     *
     * @return the active CA provider
     * @throws UnknownCaProviderException if the configured active provider is not registered
     */
    public CaProvider getActiveProvider() {
        String activeProviderName = dSignConfig.getCa().getActiveProvider();
        return getProvider(activeProviderName);
    }

    /**
     * Get all registered CA providers.
     *
     * @return unmodifiable map of provider name to provider instance
     */
    public Map<String, CaProvider> getAllProviders() {
        return Map.copyOf(providersByName);
    }
}
