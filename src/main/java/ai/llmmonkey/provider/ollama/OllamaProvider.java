package ai.llmmonkey.provider.ollama;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.api.exception.ProviderException;
import ai.llmmonkey.config.ModelDeploymentConfig;
import ai.llmmonkey.provider.EndpointType;
import ai.llmmonkey.provider.LlmProvider;
import ai.llmmonkey.provider.ProviderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@Component
public final class OllamaProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final Set<EndpointType> SUPPORTED = Set.of(
            EndpointType.CHAT_COMPLETION, EndpointType.EMBEDDING
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OllamaProvider(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        // Ollama supports OpenAI-compatible API
        return webClient.post()
                .uri(apiBase + "/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequest(request, deployment, false))
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Ollama error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.OLLAMA));
    }

    @Override
    public Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        return webClient.post()
                .uri(apiBase + "/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequest(request, deployment, true))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && !line.equals("[DONE]"))
                .mapNotNull(line -> {
                    var data = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (data.equals("[DONE]") || data.isBlank()) return null;
                    try {
                        return objectMapper.readValue(data, StreamingChatChunk.class);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse Ollama stream: {}", data, e);
                        return null;
                    }
                })
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Ollama streaming error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.OLLAMA));
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        return webClient.post()
                .uri(apiBase + "/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Ollama embedding error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.OLLAMA));
    }

    @Override
    public ProviderType type() {
        return ProviderType.OLLAMA;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    private ChatCompletionRequest buildRequest(ChatCompletionRequest request, ModelDeploymentConfig deployment, boolean stream) {
        return new ChatCompletionRequest(
                deployment.params().model(), request.messages(), request.temperature(),
                request.topP(), request.n(), stream, request.stop(),
                request.maxTokens(), request.presencePenalty(), request.frequencyPenalty(),
                request.user(), request.tools(), request.toolChoice(), request.responseFormat(),
                request.seed(), request.logprobs(), request.topLogprobs(),
                stream ? request.streamOptions() : null, request.metadata(), request.tags()
        );
    }

    private String resolveApiBase(ModelDeploymentConfig deployment) {
        var base = deployment.params().apiBase();
        if (base == null || base.isBlank()) return "http://localhost:11434";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
