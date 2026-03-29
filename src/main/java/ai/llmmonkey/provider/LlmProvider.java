package ai.llmmonkey.provider;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.config.ModelDeploymentConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Provider abstraction for LLM backends.
 * Each provider (OpenAI, Anthropic, Azure, Bedrock, Vertex AI, Cohere, Ollama)
 * implements this interface to handle provider-specific request/response translation.
 */
public interface LlmProvider {

    Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment);

    Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment);

    Mono<EmbeddingResponse> embedding(EmbeddingRequest request, ModelDeploymentConfig deployment);

    ProviderType type();

    Set<EndpointType> supportedEndpoints();

    default boolean supportsEndpoint(EndpointType endpoint) {
        return supportedEndpoints().contains(endpoint);
    }
}
