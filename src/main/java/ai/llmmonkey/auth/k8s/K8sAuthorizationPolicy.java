package ai.llmmonkey.auth.k8s;

import ai.llmmonkey.config.K8sAuthConfig;
import ai.llmmonkey.config.K8sAuthConfig.AllowlistEntry;
import ai.llmmonkey.config.K8sAuthConfig.PathPermission;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "llm-monkey.k8s-auth", name = "enabled", havingValue = "true")
public class K8sAuthorizationPolicy {

    private final List<AllowlistEntry> allowlist;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public K8sAuthorizationPolicy(K8sAuthConfig config) {
        this.allowlist = config.allowlist();
    }

    public boolean isAllowed(String namespace, String serviceAccountName, String path, String httpMethod) {
        for (AllowlistEntry entry : allowlist) {
            if (matches(entry.namespace(), namespace) && matches(entry.serviceAccount(), serviceAccountName)) {
                return hasMatchingPermission(entry.permissions(), path, httpMethod);
            }
        }
        return false;
    }

    private boolean hasMatchingPermission(List<PathPermission> permissions, String path, String httpMethod) {
        for (PathPermission perm : permissions) {
            if (pathMatcher.match(perm.pathPattern(), path) && isMethodAllowed(perm.methods(), httpMethod)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMethodAllowed(List<String> allowedMethods, String httpMethod) {
        if (allowedMethods.isEmpty()) {
            return true; // empty methods list means all methods allowed
        }
        for (String allowed : allowedMethods) {
            if (allowed.equalsIgnoreCase(httpMethod)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String pattern, String value) {
        if (pattern == null || value == null) return false;
        if ("*".equals(pattern)) return true;
        return pattern.equals(value);
    }
}
