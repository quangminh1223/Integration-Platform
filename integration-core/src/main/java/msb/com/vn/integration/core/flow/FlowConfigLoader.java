package msb.com.vn.integration.core.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads flow configurations from YAML files at startup.
 * Supports hot-reload via HotConfigService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowConfigLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private FlowConfigRoot configRoot;

    @PostConstruct
    public void loadConfig() {
        try {
            ClassPathResource resource = new ClassPathResource("flow-config.yml");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    configRoot = yamlMapper.readValue(is, FlowConfigRoot.class);
                    log.info("Loaded {} flow definitions from flow-config.yml",
                            configRoot.getFlows() != null ? configRoot.getFlows().size() : 0);
                }
            } else {
                log.warn("flow-config.yml not found, no flows configured");
                configRoot = new FlowConfigRoot();
            }
        } catch (Exception e) {
            log.error("Failed to load flow-config.yml: {}", e.getMessage(), e);
            configRoot = new FlowConfigRoot();
        }
    }

    public List<FlowDefinition> getFlowDefinitions() {
        return configRoot.getFlows() != null ? configRoot.getFlows() : List.of();
    }

    public FlowDefinition getFlowDefinition(String flowId) {
        return getFlowDefinitions().stream()
                .filter(f -> f.getFlowId().equals(flowId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Reload config (for hot-config triggered reload).
     */
    public void reload() {
        loadConfig();
    }

    @Data
    public static class FlowConfigRoot {
        private List<FlowDefinition> flows;
    }

    @Data
    public static class FlowDefinition {
        private String flowId;
        private String description;
        private boolean enabled;
        private List<StepDefinition> steps;
    }

    @Data
    public static class StepDefinition {
        private String name;
        private String type;
        private int order;
        private Map<String, String> config;
    }
}
