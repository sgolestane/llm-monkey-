package ai.llmmonkey.provider.cohere;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.api.exception.ProviderException;
import ai.llmmonkey.config.ModelDeploymentConfig;
import ai.llmmonkey.provider.EndpointType;
import ai.llmmonkey.provider.LlmProvider;
import ai.llmmonkey.provider.ProviderType;
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
public final class CohereProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CohereProvider.class);
    private static final Set<EndpointType> SUPPORTED = Set.of(
            EndpointType.CHAT_COMPLETION, EndpointType.EMBEDDING
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CohereProvider(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        var body = buildCohereRequest(request, deployment);

        return webClient.post()
                .uri(apiBase + "/v2/chat")
                .header("Authorization", "Bearer " + deployment.params().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> mapCohereResponse(response, request.model()))
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Cohere error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.COHERE));
    }

    @Override
    public Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        return chatCompletion(request, deployment)
                .flatMapMany(response -> {
                    var chunk = new StreamingChatChunk(
                            response.id(), "chat.completion.chunk", response.created(), response.model(),
                            response.choices().stream().map(c -> new StreamingChatChunk.StreamingChoice(
                                    c.index(),
                                    new StreamingChatChunk.Delta(c.message().role(), c.message().contentAsString(), null),
                                    c.finishReason()
                            )).toList(),
                            response.usage()
                    );
                    return Flux.just(chunk);
                });
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        var body = new LinkedHashMap<String, Object>();
        body.put("model", deployment.params().model());
        body.put("texts", request.input() instanceof List ? request.input() : List.of(request.input()));
        body.put("input_type", "search_document");

        return webClient.post()
                .uri(apiBase + "/v1/embed")
                .header("Authorization", "Bearer " + deployment.params().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> mapCohereEmbeddingResponse(response, request.model()))
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Cohere embedding error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.COHERE));
    }

    @Override
    public ProviderType type() {
        return ProviderType.COHERE;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    private Map<String, Object> buildCohereRequest(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", deployment.params().model());

        var messages = new ArrayList<Map<String, Object>>();
        for (var msg : request.messages()) {
            var role = switch (msg.role()) {
                case "system" -> "system";
                case "assistant" -> "assistant";
                default -> "user";
            };
            messages.add(Map.of("role", role, "content", msg.contentAsString()));
        }
        body.put("messages", messages);

        if (request.temperature() != null) body.put("temperature", request.temperature());
        if (request.maxTokens() != null) body.put("max_tokens", request.maxTokens());
        if (request.topP() != null) body.put("p", request.topP());

        return body;
    }

    private ChatCompletionResponse mapCohereResponse(JsonNode response, String modelName) {
        var text = response.path("message").path("content").path(0).path("text").asText(
                response.path("text").asText(""));

        var message = new ChatMessage("assistant", text, null, null, null);
        var finishReason = mapFinishReason(response.path("finish_reason").asText("COMPLETE"));
        var choice = new ChatCompletionResponse.Choice(0, message, finishReason);

        var inputTokens = response.path("usage").path("tokens").path("input_tokens").asInt(0);
        var outputTokens = response.path("usage").path("tokens").path("output_tokens").asInt(0);
        var usage = new UsageInfo(inputTokens, outputTokens, inputTokens + outputTokens);

        return new ChatCompletionResponse(
                response.path("id").asText("cohere-" + UUID.randomUUID().toString().substring(0, 8)),
                "chat.completion", Instant.now().getEpochSecond(),
                modelName, List.of(choice), usage, null
        );
    }

    @SuppressWarnings("unchecked")
    private EmbeddingResponse mapCohereEmbeddingResponse(JsonNode response, String model) {
        var embeddings = response.path("embeddings");
        var data = new ArrayList<EmbeddingResponse.EmbeddingData>();
        if (embeddings.isArray()) {
            for (int i = 0; i < embeddings.size(); i++) {
                var emb = embeddings.get(i);
                var values = new ArrayList<Double>();
                for (var val : emb) values.add(val.asDouble());
                data.add(new EmbeddingResponse.EmbeddingData("embedding", i, values));
            }
        }
        return new EmbeddingResponse("list", data, model, new UsageInfo(0, 0, 0));
    }

    private String mapFinishReason(String reason) {
        return switch (reason) {
            case "COMPLETE" -> "stop";
            case "MAX_TOKENS" -> "length";
            default -> "stop";
        };
    }

    private String resolveApiBase(ModelDeploymentConfig deployment) {
        var base = deployment.params().apiBase();
        if (base == null || base.isBlank()) return "https://api.cohere.ai";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
