package ai.llmmonkey.config;

import java.util.Map;

public record ProviderParamsConfig(
        String model,
        String apiKey,
        String apiBase,
        String apiVersion,
        String region,
        Map<String, String> extraHeaders
) {
    public ProviderParamsConfig {
        if (apiBase == null) apiBase = "";
        if (extraHeaders == null) extraHeaders = Map.of();
    }
}
