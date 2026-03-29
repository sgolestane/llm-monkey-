package ai.llmmonkey.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        Integer n,
        Boolean stream,
        List<String> stop,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonProperty("presence_penalty") Double presencePenalty,
        @JsonProperty("frequency_penalty") Double frequencyPenalty,
        String user,
        List<Tool> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        Integer seed,
        Boolean logprobs,
        @JsonProperty("top_logprobs") Integer topLogprobs,
        @JsonProperty("stream_options") StreamOptions streamOptions,
        Map<String, Object> metadata
) {
    public record Tool(String type, Function function) {
        public record Function(String name, String description, Map<String, Object> parameters) {}
    }

    public record ResponseFormat(String type) {}

    public record StreamOptions(@JsonProperty("include_usage") Boolean includeUsage) {}
}
