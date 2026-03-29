package ai.llmmonkey.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UsageInfo(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
) {}
