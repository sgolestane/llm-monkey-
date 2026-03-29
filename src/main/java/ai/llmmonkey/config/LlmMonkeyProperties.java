package ai.llmmonkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "llm-monkey")
public record LlmMonkeyProperties(
        String masterKey,
        List<ModelDeploymentConfig> modelList,
        RouterSettingsConfig routerSettings,
        GeneralSettingsConfig generalSettings,
        K8sAuthConfig k8sAuth
) {
    public LlmMonkeyProperties {
        if (modelList == null) modelList = List.of();
        if (routerSettings == null) routerSettings = RouterSettingsConfig.defaults();
        if (generalSettings == null) generalSettings = GeneralSettingsConfig.defaults();
        if (k8sAuth == null) k8sAuth = K8sAuthConfig.disabled();
    }
}
