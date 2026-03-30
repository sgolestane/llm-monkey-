package ai.llmmonkey.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tags & Budget E2E Tests")
class TagsAndBudgetE2eTest extends BaseE2eTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("Request Tags")
    class RequestTags {

        @Test
        @DisplayName("should accept requests with tags")
        void requestWithTags() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Tagged request"}],
                                "tags": ["project-x", "team-alpha"]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.choices[0].message.content").isNotEmpty();
        }

        @Test
        @DisplayName("should accept requests without tags")
        void requestWithoutTags() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "No tags"}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("should accept requests with empty tags list")
        void requestWithEmptyTags() {
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Empty tags"}],
                                "tags": []
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("should accept tags on embedding requests")
        void embeddingWithTags() {
            client.post().uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-embedding",
                                "input": "Hello world",
                                "tags": ["embedding-project"]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data[0].embedding").isArray();
        }

        @Test
        @DisplayName("should accept tags on completion requests")
        void completionWithTags() {
            client.post().uri("/v1/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "prompt": "Once upon",
                                "tags": ["completion-tag"]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("Tag Budgets")
    class TagBudgets {

        @Test
        @DisplayName("should create a tag budget")
        void createTagBudget() {
            client.post().uri("/tag/budget/new")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "tag": "e2e-budget-create",
                                "max_budget": 100.00,
                                "budget_duration": "30d"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.tag").isEqualTo("e2e-budget-create")
                    .jsonPath("$.max_budget").isEqualTo(100.0);
        }

        @Test
        @DisplayName("should update a tag budget")
        void updateTagBudget() {
            // Create first
            client.post().uri("/tag/budget/new")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"tag": "e2e-budget-update", "max_budget": 50.00}
                            """)
                    .exchange()
                    .expectStatus().isOk();

            // Update
            client.post().uri("/tag/budget/update")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"tag": "e2e-budget-update", "max_budget": 200.00}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.max_budget").isEqualTo(200.0);
        }

        @Test
        @DisplayName("should get tag budget info")
        void getTagBudgetInfo() {
            // Create
            client.post().uri("/tag/budget/new")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"tag": "e2e-budget-info", "max_budget": 75.00, "budget_duration": "7d"}
                            """)
                    .exchange()
                    .expectStatus().isOk();

            // Get info
            client.get().uri("/tag/budget/info?tag=e2e-budget-info")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.tag").isEqualTo("e2e-budget-info")
                    .jsonPath("$.max_budget").isEqualTo(75.0)
                    .jsonPath("$.budget_duration").isEqualTo("7d");
        }

        @Test
        @DisplayName("should list all tag budgets")
        void listTagBudgets() {
            // Create a budget
            client.post().uri("/tag/budget/new")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"tag": "e2e-budget-list", "max_budget": 25.00}
                            """)
                    .exchange()
                    .expectStatus().isOk();

            client.get().uri("/tag/budget/list")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").value(len ->
                            assertThat((Integer) len).isGreaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("should delete a tag budget")
        void deleteTagBudget() {
            // Create
            client.post().uri("/tag/budget/new")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"tag": "e2e-budget-delete", "max_budget": 10.00}
                            """)
                    .exchange()
                    .expectStatus().isOk();

            // Delete
            client.post().uri("/tag/budget/delete")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"tag": "e2e-budget-delete"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("deleted");

            // Verify gone
            client.get().uri("/tag/budget/info?tag=e2e-budget-delete")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("should return 404 for unknown tag budget")
        void unknownTagBudget() {
            client.get().uri("/tag/budget/info?tag=nonexistent-tag-xyz")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("Tag Spend Reporting")
    @Disabled("Native PostgreSQL array queries (ANY(tags)) not compatible with H2 test database")
    class TagSpendReporting {

        @Test
        @DisplayName("should return spend report for a tag")
        void spendReportAllTime() {
            // Make a request with tags so there's data
            client.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "model": "mock-gpt-4",
                                "messages": [{"role": "user", "content": "Spend tracking"}],
                                "tags": ["e2e-spend-report"]
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();

            // Query spend (may be zero if async tracking hasn't completed yet, but endpoint should work)
            client.get().uri("/tag/spend?tag=e2e-spend-report")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.tag").isEqualTo("e2e-spend-report")
                    .jsonPath("$.total_spend").isNumber()
                    .jsonPath("$.request_count").isNumber()
                    .jsonPath("$.total_tokens").isNumber();
        }

        @Test
        @DisplayName("should return spend report with period filter")
        void spendReportWithPeriod() {
            client.get().uri("/tag/spend?tag=e2e-spend-period&period=7d")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.tag").isEqualTo("e2e-spend-period")
                    .jsonPath("$.period").isEqualTo("7d");
        }

        @Test
        @DisplayName("should return zero spend for unused tag")
        void zeroSpendForNewTag() {
            client.get().uri("/tag/spend?tag=never-used-tag-abc")
                    .header("Authorization", "Bearer " + MASTER_KEY)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.tag").isEqualTo("never-used-tag-abc")
                    .jsonPath("$.total_spend").isEqualTo(0)
                    .jsonPath("$.request_count").isEqualTo(0);
        }
    }
}
