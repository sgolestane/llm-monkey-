package ai.llmmonkey.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatMessage(
        String role,
        Object content,
        String name,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {
    public record ToolCall(
            String id,
            String type,
            Function function
    ) {
        public record Function(String name, String arguments) {}
    }

    public String contentAsString() {
        if (content instanceof String s) return s;
        if (content == null) return "";
        return content.toString();
    }
}
