package ai.llmmonkey.config;

import java.util.List;
import java.util.Map;

public record RouterSettingsConfig(
        String routingStrategy,
        int numRetries,
        int timeoutSeconds,
        int allowedFails,
        int cooldownSeconds,
        Map<String, List<String>> fallbacks
) {
    public RouterSettingsConfig {
        if (routingStrategy == null) routingStrategy = "round-robin";
        if (fallbacks == null) fallbacks = Map.of();
    }

    public static RouterSettingsConfig defaults() {
        return new RouterSettingsConfig("round-robin", 2, 300, 3, 60, Map.of());
    }
}
