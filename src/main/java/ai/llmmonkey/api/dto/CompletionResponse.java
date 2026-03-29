package ai.llmmonkey.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<CompletionChoice> choices,
        UsageInfo usage
) {
    public record CompletionChoice(
            int index,
            String text,
            @JsonProperty("finish_reason") String finishReason
    ) {}
}
