package ai.llmmonkey.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record StreamingChatChunk(
        String id,
        String object,
        long created,
        String model,
        List<StreamingChoice> choices,
        UsageInfo usage
) {
    public record StreamingChoice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    public record Delta(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ChatMessage.ToolCall> toolCalls
    ) {}
}
