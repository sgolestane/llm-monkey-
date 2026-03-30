package ai.llmmonkey.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Authentication E2E Tests")
class AuthenticationE2eTest extends BaseE2eTest {

    @Nested
    @DisplayName("Master Key Auth")
    class MasterKeyAuth {

        @Test
        @DisplayName("should allow requests with valid master key")
        void validMasterKey() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.choices[0].message.content").isNotEmpty();
        }

        @Test
        @DisplayName("should allow admin endpoints with master key")
        void masterKeyAdminAccess() {
            client.get().uri("/key/list")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("No Auth")
    class NoAuth {

        @Test
        @DisplayName("should reject requests without auth header")
        void noAuthHeader() {
            client.post().uri("/v1/chat/completions")
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should reject requests with empty bearer token")
        void emptyBearerToken() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer ")
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should reject requests with non-Bearer auth")
        void nonBearerAuth() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Basic dXNlcjpwYXNz")
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Invalid Key")
    class InvalidKey {

        @Test
        @DisplayName("should reject requests with invalid master key")
        void invalidMasterKey() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer wrong-master-key")
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should reject requests with invalid virtual key")
        void invalidVirtualKey() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer sk-invalid-key-12345")
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Virtual Key Auth")
    class VirtualKeyAuth {

        @Test
        @DisplayName("should allow requests with valid virtual key")
        void validVirtualKey() {
            // Generate a virtual key first
            String key = generateVirtualKey("auth-test-key");

            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello from virtual key"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.choices[0].message.content").isNotEmpty();
        }

        @Test
        @DisplayName("should reject nonexistent virtual key")
        void nonexistentVirtualKey() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer sk-lm-nonexistent00000000000000000")
                    .header("Content-Type", "application/json")
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Public Endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("should allow health check without auth")
        void healthNoAuth() {
            client.get().uri("/health")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("should allow readiness check without auth")
        void readinessNoAuth() {
            client.get().uri("/health/readiness")
                    .exchange()
                    .expectStatus().isOk();
        }

        // Prometheus actuator endpoint test omitted - requires actuator web config
    }

    private String generateVirtualKey(String keyName) {
        var result = client.post().uri("/key/generate")
                .header("Authorization", "Bearer " + MASTER_KEY)
                .header("Content-Type", "application/json")
                .bodyValue("{\"key_name\": \"" + keyName + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.key").exists()
                .returnResult()
                .getResponseBody();

        // Extract the key from JSON response
        String body = new String(result);
        int keyStart = body.indexOf("\"key\":\"") + 7;
        int keyEnd = body.indexOf("\"", keyStart);
        return body.substring(keyStart, keyEnd);
    }
}
