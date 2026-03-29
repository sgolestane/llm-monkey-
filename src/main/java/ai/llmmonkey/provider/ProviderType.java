package ai.llmmonkey.provider;

public enum ProviderType {
    OPENAI,
    ANTHROPIC,
    AZURE,
    BEDROCK,
    VERTEX_AI,
    COHERE,
    OLLAMA;

    public static ProviderType fromString(String value) {
        return switch (value.toLowerCase()) {
            case "openai" -> OPENAI;
            case "anthropic" -> ANTHROPIC;
            case "azure", "azure_openai" -> AZURE;
            case "bedrock", "aws_bedrock" -> BEDROCK;
            case "vertex_ai", "vertexai", "google" -> VERTEX_AI;
            case "cohere" -> COHERE;
            case "ollama" -> OLLAMA;
            default -> throw new IllegalArgumentException("Unknown provider: " + value);
        };
    }
}
