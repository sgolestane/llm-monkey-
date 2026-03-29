package ai.llmmonkey.auth.k8s;

import ai.llmmonkey.config.K8sAuthConfig;
import ai.llmmonkey.config.K8sAuthConfig.AllowlistEntry;
import ai.llmmonkey.config.K8sAuthConfig.PathPermission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class K8sAuthorizationPolicyTest {

    private K8sAuthorizationPolicy policyWith(List<AllowlistEntry> allowlist) {
        K8sAuthConfig config = new K8sAuthConfig(
                true, "llm-monkey", null, null, null, 30, allowlist);
        return new K8sAuthorizationPolicy(config);
    }

    @Test
    void allowsExactPathMatch() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of("POST"))
                ))
        ));

        assertTrue(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "POST"));
    }

    @Test
    void deniesWrongMethod() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of("POST"))
                ))
        ));

        assertFalse(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "GET"));
    }

    @Test
    void deniesWrongPath() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of("POST"))
                ))
        ));

        assertFalse(policy.isAllowed("billing", "billing-api", "/v1/embeddings", "POST"));
    }

    @Test
    void deniesUnknownCaller() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of("POST"))
                ))
        ));

        assertFalse(policy.isAllowed("unknown", "unknown-sa", "/v1/chat/completions", "POST"));
    }

    @Test
    void deniesWrongNamespace() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of("POST"))
                ))
        ));

        assertFalse(policy.isAllowed("other-ns", "billing-api", "/v1/chat/completions", "POST"));
    }

    @Test
    void wildcardPathMatchesSubPaths() {
        var policy = policyWith(List.of(
                new AllowlistEntry("planning", "planning-api", List.of(
                        new PathPermission("/v1/**", List.of("GET", "POST"))
                ))
        ));

        assertTrue(policy.isAllowed("planning", "planning-api", "/v1/chat/completions", "POST"));
        assertTrue(policy.isAllowed("planning", "planning-api", "/v1/embeddings", "GET"));
        assertTrue(policy.isAllowed("planning", "planning-api", "/v1/models", "GET"));
    }

    @Test
    void emptyMethodsAllowsAllMethods() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of())
                ))
        ));

        assertTrue(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "POST"));
        assertTrue(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "GET"));
        assertTrue(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "DELETE"));
    }

    @Test
    void emptyAllowlistDeniesAll() {
        var policy = policyWith(List.of());

        assertFalse(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "POST"));
    }

    @Test
    void methodMatchIsCaseInsensitive() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of("POST"))
                ))
        ));

        assertTrue(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "post"));
        assertTrue(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "Post"));
    }

    @Test
    void multipleEntriesWorkIndependently() {
        var policy = policyWith(List.of(
                new AllowlistEntry("billing", "billing-api", List.of(
                        new PathPermission("/v1/chat/completions", List.of("POST"))
                )),
                new AllowlistEntry("planning", "planning-api", List.of(
                        new PathPermission("/v1/models", List.of("GET"))
                ))
        ));

        assertTrue(policy.isAllowed("billing", "billing-api", "/v1/chat/completions", "POST"));
        assertFalse(policy.isAllowed("billing", "billing-api", "/v1/models", "GET"));
        assertTrue(policy.isAllowed("planning", "planning-api", "/v1/models", "GET"));
        assertFalse(policy.isAllowed("planning", "planning-api", "/v1/chat/completions", "POST"));
    }
}
