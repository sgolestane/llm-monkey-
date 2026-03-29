package ai.llmmonkey.provider.vertexai;

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
public final class VertexAiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(VertexAiProvider.class);
    private static final Set<EndpointType> SUPPORTED = Set.of(
            EndpointType.CHAT_COMPLETION, EndpointType.EMBEDDING
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public VertexAiProvider(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var uri = buildUri(deployment);
        var body = buildVertexRequest(request);

        return webClient.post()
                .uri(uri)
                .header("Authorization", "Bearer " + deployment.params().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> mapVertexResponse(response, request.model()))
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Vertex AI error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.VERTEX_AI));
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
        return Mono.error(new ProviderException("Vertex AI embeddings not yet implemented", 501, ProviderType.VERTEX_AI));
    }

    @Override
    public ProviderType type() {
        return ProviderType.VERTEX_AI;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    private String buildUri(ModelDeploymentConfig deployment) {
        var region = deployment.params().region() != null ? deployment.params().region() : "us-central1";
        var projectId = deployment.params().apiBase(); // Use apiBase as project ID for Vertex
        var model = deployment.params().model();
        return String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                region, projectId, region, model
        );
    }

    private Map<String, Object> buildVertexRequest(ChatCompletionRequest request) {
        var contents = new ArrayList<Map<String, Object>>();
        for (var msg : request.messages()) {
            if ("system".equals(msg.role())) continue;
            var role = "user".equals(msg.role()) ? "user" : "model";
            contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", msg.contentAsString()))
            ));
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("contents", contents);

        var generationConfig = new LinkedHashMap<String, Object>();
        if (request.temperature() != null) generationConfig.put("temperature", request.temperature());
        if (request.maxTokens() != null) generationConfig.put("maxOutputTokens", request.maxTokens());
        if (request.topP() != null) generationConfig.put("topP", request.topP());
        if (!generationConfig.isEmpty()) body.put("generationConfig", generationConfig);

        // System instruction
        request.messages().stream()
                .filter(m -> "system".equals(m.role()))
                .findFirst()
                .ifPresent(sys -> body.put("systemInstruction",
                        Map.of("parts", List.of(Map.of("text", sys.contentAsString())))));

        return body;
    }

    private ChatCompletionResponse mapVertexResponse(JsonNode response, String modelName) {
        var candidates = response.path("candidates");
        var text = "";
        var finishReason = "stop";
        if (candidates.isArray() && !candidates.isEmpty()) {
            var candidate = candidates.get(0);
            var parts = candidate.path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                text = parts.get(0).path("text").asText("");
            }
            finishReason = mapFinishReason(candidate.path("finishReason").asText("STOP"));
        }

        var message = new ChatMessage("assistant", text, null, null, null);
        var choice = new ChatCompletionResponse.Choice(0, message, finishReason);

        var usageMeta = response.path("usageMetadata");
        var inputTokens = usageMeta.path("promptTokenCount").asInt(0);
        var outputTokens = usageMeta.path("candidatesTokenCount").asInt(0);
        var usage = new UsageInfo(inputTokens, outputTokens, inputTokens + outputTokens);

        return new ChatCompletionResponse(
                "vertex-" + UUID.randomUUID().toString().substring(0, 8),
                "chat.completion", Instant.now().getEpochSecond(),
                modelName, List.of(choice), usage, null
        );
    }

    private String mapFinishReason(String vertexReason) {
        return switch (vertexReason) {
            case "STOP" -> "stop";
            case "MAX_TOKENS" -> "length";
            case "SAFETY" -> "content_filter";
            default -> "stop";
        };
    }
}
