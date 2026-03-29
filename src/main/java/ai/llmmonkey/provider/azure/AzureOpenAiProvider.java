package ai.llmmonkey.provider.azure;

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
public final class AzureOpenAiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiProvider.class);
    private static final String DEFAULT_API_VERSION = "2024-02-01";
    private static final Set<EndpointType> SUPPORTED = Set.of(
            EndpointType.CHAT_COMPLETION, EndpointType.COMPLETION,
            EndpointType.EMBEDDING, EndpointType.IMAGE_GENERATION
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AzureOpenAiProvider(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        return webClient.post()
                .uri(buildUri(deployment, "/chat/completions"))
                .header("api-key", deployment.params().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(withModel(request, deployment, false))
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Azure OpenAI error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.AZURE));
    }

    @Override
    public Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        return webClient.post()
                .uri(buildUri(deployment, "/chat/completions"))
                .header("api-key", deployment.params().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(withModel(request, deployment, true))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && !line.equals("[DONE]"))
                .mapNotNull(line -> {
                    var data = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (data.equals("[DONE]") || data.isBlank()) return null;
                    try {
                        return objectMapper.readValue(data, StreamingChatChunk.class);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse Azure stream: {}", data, e);
                        return null;
                    }
                })
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Azure streaming error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.AZURE));
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request, ModelDeploymentConfig deployment) {
        return webClient.post()
                .uri(buildUri(deployment, "/embeddings"))
                .header("api-key", deployment.params().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Azure embedding error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.AZURE));
    }

    @Override
    public ProviderType type() {
        return ProviderType.AZURE;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    private String buildUri(ModelDeploymentConfig deployment, String endpoint) {
        var apiBase = deployment.params().apiBase();
        var model = deployment.params().model();
        var apiVersion = deployment.params().apiVersion() != null
                ? deployment.params().apiVersion() : DEFAULT_API_VERSION;
        return apiBase + "/openai/deployments/" + model + endpoint + "?api-version=" + apiVersion;
    }

    private ChatCompletionRequest withModel(ChatCompletionRequest request, ModelDeploymentConfig deployment, boolean stream) {
        return new ChatCompletionRequest(
                deployment.params().model(), request.messages(), request.temperature(),
                request.topP(), request.n(), stream, request.stop(),
                request.maxTokens(), request.presencePenalty(), request.frequencyPenalty(),
                request.user(), request.tools(), request.toolChoice(), request.responseFormat(),
                request.seed(), request.logprobs(), request.topLogprobs(),
                stream ? request.streamOptions() : null, request.metadata(), request.tags()
        );
    }
}
