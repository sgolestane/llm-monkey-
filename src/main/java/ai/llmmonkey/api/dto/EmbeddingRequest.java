package ai.llmmonkey.api.dto;

public record EmbeddingRequest(
        String model,
        Object input,
        String user,
        String encodingFormat
) {}
