package ai.llmmonkey.e2e;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Chat Completion E2E Tests")
class ChatCompletionE2eTest extends BaseE2eTest {

    @Nested
    @DisplayName("Non-Streaming Chat Completions")
    class NonStreaming {

        @Test
        @DisplayName("should return a valid chat completion response")
        void basicChatCompletion() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [
                                    {"role": "system", "content": "You are helpful."},
                                    {"role": "user", "content": "What is 2+2?"}
                                ]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").value(id -> assertThat((String) id).startsWith("chatcmpl-mock-"))
                    .jsonPath("$.object").isEqualTo("chat.completion")
                    .jsonPath("$.model").isEqualTo("mock-gpt-4")
                    .jsonPath("$.choices").isArray()
                    .jsonPath("$.choices[0].index").isEqualTo(0)
                    .jsonPath("$.choices[0].message.role").isEqualTo("assistant")
                    .jsonPath("$.choices[0].message.content").value(c ->
                            assertThat((String) c).contains("What is 2+2?"))
                    .jsonPath("$.choices[0].finish_reason").isEqualTo("stop")
                    .jsonPath("$.usage.prompt_tokens").isEqualTo(10)
                    .jsonPath("$.usage.completion_tokens").isEqualTo(15)
                    .jsonPath("$.usage.total_tokens").isEqualTo(25);
        }

        @Test
        @DisplayName("should route to correct model")
        void routeToCorrectModel() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-claude",
                                "messages": [{"role": "user", "content": "Hello Claude"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.model").isEqualTo("mock-claude");
        }

        @Test
        @DisplayName("should handle temperature and max_tokens parameters")
        void withOptionalParams() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Hello"}],
                                "temperature": 0.5,
                                "max_tokens": 100,
                                "top_p": 0.9
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.choices[0].message.content").isNotEmpty();
        }

        @Test
        @DisplayName("should handle multi-turn conversations")
        void multiTurnConversation() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [
                                    {"role": "system", "content": "You are a math tutor."},
                                    {"role": "user", "content": "What is calculus?"},
                                    {"role": "assistant", "content": "Calculus is a branch of math."},
                                    {"role": "user", "content": "Tell me more about derivatives"}
                                ]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.choices[0].message.content").value(c ->
                            assertThat((String) c).contains("derivatives"));
        }

        @Test
        @DisplayName("should increment mock provider request counter")
        void requestCounting() {
            int before = mockProvider.getRequestCount();

            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Test"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();

            assertThat(mockProvider.getRequestCount()).isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("Streaming Chat Completions")
    class Streaming {

        @Test
        @Disabled("SSE streaming endpoint has content negotiation issue with dual @PostMapping - to be fixed separately")
        @DisplayName("should return streaming SSE response")
        void streamingResponse() {
            var result = client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Stream test"}],
                                "stream": true
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult(String.class);

            // Collect SSE events from the Flux
            var events = result.getResponseBody()
                    .collectList()
                    .block(java.time.Duration.ofSeconds(10));

            assertThat(events).isNotNull();
            assertThat(events).isNotEmpty();
            // At least one event should contain the mock stream ID
            String allEvents = String.join("", events);
            assertThat(allEvents).contains("chatcmpl-mock-stream-");
        }
    }

    @Nested
    @DisplayName("Text Completions (Legacy)")
    class TextCompletions {

        @Test
        @DisplayName("should handle legacy completion endpoint")
        void legacyCompletion() {
            client.post().uri("/v1/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "prompt": "Once upon a time"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.choices[0].message.content").value(c ->
                            assertThat((String) c).contains("Once upon a time"));
        }
    }

    @Nested
    @DisplayName("Embeddings")
    class Embeddings {

        @Test
        @DisplayName("should return embedding vectors")
        void basicEmbedding() {
            client.post().uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-embedding",
                                "input": "Hello world"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.object").isEqualTo("list")
                    .jsonPath("$.data").isArray()
                    .jsonPath("$.data[0].object").isEqualTo("embedding")
                    .jsonPath("$.data[0].index").isEqualTo(0)
                    .jsonPath("$.data[0].embedding").isArray()
                    .jsonPath("$.data[0].embedding[0]").isEqualTo(0.1)
                    .jsonPath("$.model").isEqualTo("mock-embedding")
                    .jsonPath("$.usage.prompt_tokens").isEqualTo(8)
                    .jsonPath("$.usage.total_tokens").isEqualTo(8);
        }
    }
}
