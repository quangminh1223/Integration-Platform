package msb.com.vn.integration.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Loads routing configuration from YAML and registers routes in ContentBasedRouter.
 * Supports hot-reload via HotConfigService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoutingConfigLoader {

    private final ContentBasedRouter contentBasedRouter;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @PostConstruct
    public void loadConfig() {
        try {
            ClassPathResource resource = new ClassPathResource("routing-config.yml");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    RoutingConfigRoot root = yamlMapper.readValue(is, RoutingConfigRoot.class);
                    if (root.getRouting() != null && root.getRouting().getRoutes() != null) {
                        contentBasedRouter.reloadRoutes(root.getRouting().getRoutes());
                        log.info("Loaded {} routing entries from routing-config.yml",
                                root.getRouting().getRoutes().size());
                    }
                }
            } else {
                log.warn("routing-config.yml not found, no routes configured");
            }
        } catch (Exception e) {
            log.error("Failed to load routing-config.yml: {}", e.getMessage(), e);
        }
    }

    public void reload() {
        loadConfig();
    }

    @Data
    public static class RoutingConfigRoot {
        private RoutingSection routing;
    }

    @Data
    public static class RoutingSection {
        private List<RoutingConfig> routes;
    }
}
