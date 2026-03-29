package ai.llmmonkey.api.dto;

public record EmbeddingRequest(
        String model,
        Object input,
        String user,
        String encodingFormat,
        java.util.Map<String, Object> metadata,
        java.util.List<String> tags
) {}
