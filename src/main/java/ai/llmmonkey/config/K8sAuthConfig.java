package ai.llmmonkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "llm-monkey.k8s-auth")
public record K8sAuthConfig(
        boolean enabled,
        String audience,
        String apiServerUrl,
        String caCertPath,
        String serviceAccountTokenPath,
        long tokenCacheTtlSeconds,
        List<AllowlistEntry> allowlist
) {
    public K8sAuthConfig {
        if (apiServerUrl == null || apiServerUrl.isBlank()) {
            apiServerUrl = "https://kubernetes.default.svc";
        }
        if (caCertPath == null || caCertPath.isBlank()) {
            caCertPath = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
        }
        if (serviceAccountTokenPath == null || serviceAccountTokenPath.isBlank()) {
            serviceAccountTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";
        }
        if (tokenCacheTtlSeconds <= 0) {
            tokenCacheTtlSeconds = 30;
        }
        if (allowlist == null) {
            allowlist = List.of();
        }
    }

    public static K8sAuthConfig disabled() {
        return new K8sAuthConfig(false, null, null, null, null, 30, List.of());
    }

    public record AllowlistEntry(
            String namespace,
            String serviceAccount,
            List<PathPermission> permissions
    ) {
        public AllowlistEntry {
            if (permissions == null) permissions = List.of();
        }
    }

    public record PathPermission(
            String pathPattern,
            List<String> methods
    ) {
        public PathPermission {
            if (methods == null) methods = List.of();
        }
    }
}
