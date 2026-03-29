package ai.llmmonkey.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CompletionRequest(
        String model,
        Object prompt,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        Integer n,
        Boolean stream,
        List<String> stop,
        @JsonProperty("presence_penalty") Double presencePenalty,
        @JsonProperty("frequency_penalty") Double frequencyPenalty,
        String user,
        Integer seed,
        java.util.Map<String, Object> metadata,
        java.util.List<String> tags
) {}
