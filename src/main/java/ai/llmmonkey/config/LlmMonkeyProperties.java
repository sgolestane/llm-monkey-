package ai.llmmonkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "llm-monkey")
public record LlmMonkeyProperties(
        String masterKey,
        List<ModelDeploymentConfig> modelList,
        RouterSettingsConfig routerSettings,
        GeneralSettingsConfig generalSettings
) {
    public LlmMonkeyProperties {
        if (modelList == null) modelList = List.of();
        if (routerSettings == null) routerSettings = RouterSettingsConfig.defaults();
        if (generalSettings == null) generalSettings = GeneralSettingsConfig.defaults();
    }
}
