package ai.llmmonkey.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Key Management E2E Tests")
class KeyManagementE2eTest extends BaseE2eTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("Generate Key")
    class GenerateKey {

        @Test
        @DisplayName("should generate a new virtual key")
        void generateBasicKey() {
            client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"key_name": "test-service-key"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.key").value(k -> assertThat((String) k).startsWith("sk-lm-"))
                    .jsonPath("$.key_name").isEqualTo("test-service-key")
                    .jsonPath("$.token").isNotEmpty();
        }

        @Test
        @DisplayName("should generate key with budget and rate limits")
        void generateKeyWithLimits() {
            client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "key_name": "limited-key",
                                "max_budget": 50.00,
                                "tpm_limit": 10000,
                                "rpm_limit": 100,
                                "budget_duration": "30d"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.key").value(k -> assertThat((String) k).startsWith("sk-lm-"))
                    .jsonPath("$.max_budget").isEqualTo(50.0);
        }

        @Test
        @DisplayName("should generate key with model restrictions")
        void generateKeyWithModels() {
            client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "key_name": "model-restricted-key",
                                "models": ["mock-gpt-4"]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.key").exists();
        }
    }

    @Nested
    @DisplayName("List Keys")
    class ListKeys {

        @Test
        @DisplayName("should list all keys")
        void listAllKeys() {
            // Generate a key first
            client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"key_name": "list-test-key"}
                            """)
                    .exchange()
                    .expectStatus().isOk();

            client.get().uri("/key/list")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").value(len ->
                            assertThat((Integer) len).isGreaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("should filter keys by userId")
        void listKeysByUser() {
            // Generate a key for a specific user
            client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"key_name": "user-filter-key", "user_id": "test-user-filter"}
                            """)
                    .exchange()
                    .expectStatus().isOk();

            client.get().uri("/key/list?userId=test-user-filter")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$[0].user_id").isEqualTo("test-user-filter");
        }
    }

    @Nested
    @DisplayName("Update Key")
    class UpdateKey {

        @Test
        @DisplayName("should update key budget")
        void updateKeyBudget() throws Exception {
            // Generate a key
            byte[] responseBytes = client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"key_name": "update-test-key", "max_budget": 10.00}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().returnResult().getResponseBody();

            JsonNode genResponse = mapper.readTree(responseBytes);
            String tokenHash = genResponse.get("token").asText();

            // Update the key
            client.post().uri("/key/update/" + tokenHash)
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"max_budget": 100.00, "key_name": "updated-key"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.key_name").isEqualTo("updated-key")
                    .jsonPath("$.max_budget").isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("Delete Key")
    class DeleteKey {

        @Test
        @DisplayName("should delete a key and prevent its use")
        void deleteKeyAndVerify() throws Exception {
            // Generate a key
            byte[] responseBytes = client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"key_name": "delete-test-key"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().returnResult().getResponseBody();

            JsonNode genResponse = mapper.readTree(responseBytes);
            String plainKey = genResponse.get("key").asText();
            String tokenHash = genResponse.get("token").asText();

            // Verify key works
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + plainKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "test"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();

            // Delete the key
            client.post().uri("/key/delete/" + tokenHash)
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk();

            // Verify key no longer works
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + plainKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "test"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Key Info")
    class KeyInfo {

        @Test
        @DisplayName("should retrieve key info by hash")
        void getKeyInfo() throws Exception {
            byte[] responseBytes = client.post().uri("/key/generate")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"key_name": "info-test-key", "user_id": "info-user"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().returnResult().getResponseBody();

            JsonNode genResponse = mapper.readTree(responseBytes);
            String tokenHash = genResponse.get("token").asText();

            client.get().uri("/key/info/" + tokenHash)
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.key_name").isEqualTo("info-test-key")
                    .jsonPath("$.user_id").isEqualTo("info-user");
        }

        @Test
        @DisplayName("should return 404 for unknown key hash")
        void unknownKeyHash() {
            client.get().uri("/key/info/nonexistent-hash-value")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
