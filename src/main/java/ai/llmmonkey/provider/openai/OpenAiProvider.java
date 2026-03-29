package ai.llmmonkey.provider.openai;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.api.exception.ProviderException;
import ai.llmmonkey.config.ModelDeploymentConfig;
import ai.llmmonkey.provider.EndpointType;
import ai.llmmonkey.provider.LlmProvider;
import ai.llmmonkey.provider.ProviderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
public final class OpenAiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final Set<EndpointType> SUPPORTED = Set.of(
            EndpointType.CHAT_COMPLETION, EndpointType.COMPLETION,
            EndpointType.EMBEDDING, EndpointType.IMAGE_GENERATION,
            EndpointType.AUDIO_SPEECH, EndpointType.AUDIO_TRANSCRIPTION
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        return webClient.post()
                .uri(apiBase + "/chat/completions")
                .headers(h -> setHeaders(h, deployment))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(withModel(request, deployment))
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("OpenAI API error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.OPENAI));
    }

    @Override
    public Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        return webClient.post()
                .uri(apiBase + "/chat/completions")
                .headers(h -> setHeaders(h, deployment))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(withModelAndStream(request, deployment))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && !line.equals("[DONE]"))
                .mapNotNull(line -> {
                    var data = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (data.equals("[DONE]") || data.isBlank()) return null;
                    try {
                        return objectMapper.readValue(data, StreamingChatChunk.class);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse streaming chunk: {}", data, e);
                        return null;
                    }
                })
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("OpenAI streaming error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.OPENAI));
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        return webClient.post()
                .uri(apiBase + "/embeddings")
                .headers(h -> setHeaders(h, deployment))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("OpenAI embedding error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.OPENAI));
    }

    @Override
    public ProviderType type() {
        return ProviderType.OPENAI;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    private void setHeaders(org.springframework.http.HttpHeaders headers, ModelDeploymentConfig deployment) {
        headers.setBearerAuth(deployment.params().apiKey());
        deployment.params().extraHeaders().forEach(headers::set);
    }

    private String resolveApiBase(ModelDeploymentConfig deployment) {
        var base = deployment.params().apiBase();
        if (base == null || base.isBlank()) return "https://api.openai.com/v1";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private ChatCompletionRequest withModel(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        return new ChatCompletionRequest(
                deployment.params().model(), request.messages(), request.temperature(),
                request.topP(), request.n(), false, request.stop(),
                request.maxTokens(), request.presencePenalty(), request.frequencyPenalty(),
                request.user(), request.tools(), request.toolChoice(), request.responseFormat(),
                request.seed(), request.logprobs(), request.topLogprobs(), null, request.metadata()
        );
    }

    private ChatCompletionRequest withModelAndStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        return new ChatCompletionRequest(
                deployment.params().model(), request.messages(), request.temperature(),
                request.topP(), request.n(), true, request.stop(),
                request.maxTokens(), request.presencePenalty(), request.frequencyPenalty(),
                request.user(), request.tools(), request.toolChoice(), request.responseFormat(),
                request.seed(), request.logprobs(), request.topLogprobs(), request.streamOptions(),
                request.metadata()
        );
    }
}
