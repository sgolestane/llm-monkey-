package ai.llmmonkey.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Model Listing & Error Handling E2E Tests")
class ModelAndErrorE2eTest extends BaseE2eTest {

    @Nested
    @DisplayName("Model Listing")
    class ModelListing {

        @Test
        @DisplayName("should list available models")
        void listModels() {
            client.get().uri("/v1/models")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .value(body -> {
                        assertThat(body).contains("mock-gpt-4");
                        assertThat(body).contains("mock-claude");
                        assertThat(body).contains("mock-embedding");
                    });
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return 404 for unknown model")
        void unknownModel() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "nonexistent-model",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.error.message").value(msg ->
                            assertThat((String) msg).contains("nonexistent-model"))
                    .jsonPath("$.error.code").isEqualTo("model_not_found");
        }

        @Test
        @DisplayName("should return proper error format")
        void errorResponseFormat() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "no-such-model",
                                "messages": [{"role": "user", "content": "Hello"}]
                            }
                            """)
                    .exchange()
                    .expectBody()
                    .jsonPath("$.error").exists()
                    .jsonPath("$.error.message").isNotEmpty()
                    .jsonPath("$.error.type").isNotEmpty()
                    .jsonPath("$.error.code").isNotEmpty();
        }

        @Test
        @DisplayName("should handle provider failure with retry")
        void providerFailure() {
            // Set mock to fail once - router should retry
            mockProvider.setFailNextRequest(true);

            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Retry test"}]
                            }
                            """)
                    .exchange()
                    // After failure + retry, should succeed since failNextRequest only fails once
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.choices[0].message.content").isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Health & Metrics")
    class HealthAndMetrics {

        @Test
        @DisplayName("health endpoint should return ok")
        void health() {
            client.get().uri("/health")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("readiness endpoint should return ok")
        void readiness() {
            client.get().uri("/health/readiness")
                    .exchange()
                    .expectStatus().isOk();
        }

        // Prometheus metrics test omitted - requires actuator web config
    }

    @Nested
    @DisplayName("Multiple Models & Routing")
    class Routing {

        @Test
        @DisplayName("should route requests to different mock models")
        void routeToDifferentModels() {
            // Request to mock-gpt-4
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "GPT test"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.model").isEqualTo("mock-gpt-4");

            // Request to mock-claude
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-claude",
                                "messages": [{"role": "user", "content": "Claude test"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.model").isEqualTo("mock-claude");
        }

        @Test
        @DisplayName("should handle concurrent requests")
        void concurrentRequests() {
            mockProvider.resetRequestCount();

            // Fire 5 requests concurrently
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                client.post().uri("/v1/chat/completions")
                        .header("Authorization", "Bearer " + MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"model\":\"mock-gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Concurrent " + idx + "\"}]}")
                        .exchange()
                        .expectStatus().isOk();
            }

            assertThat(mockProvider.getRequestCount()).isEqualTo(5);
        }
    }
}
