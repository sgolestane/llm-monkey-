package ai.llmmonkey.provider.bedrock;

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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

import java.time.Instant;
import java.util.*;

@Component
public final class BedrockProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(BedrockProvider.class);
    private static final Set<EndpointType> SUPPORTED = Set.of(EndpointType.CHAT_COMPLETION, EndpointType.EMBEDDING);

    private final ObjectMapper objectMapper;

    public BedrockProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        return Mono.fromCallable(() -> {
            var region = deployment.params().region() != null
                    ? Region.of(deployment.params().region()) : Region.US_EAST_1;

            try (var client = BedrockRuntimeClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build()) {

                var body = buildBedrockBody(request, deployment);
                var invokeRequest = InvokeModelRequest.builder()
                        .modelId(deployment.params().model())
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(body)))
                        .build();

                var response = client.invokeModel(invokeRequest);
                var responseJson = objectMapper.readTree(response.body().asUtf8String());
                return mapBedrockResponse(responseJson, request.model());
            }
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorMap(e -> !(e instanceof ProviderException),
                  e -> new ProviderException("Bedrock error: " + e.getMessage(), 502, ProviderType.BEDROCK));
    }

    @Override
    public Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        // Simplified: convert non-streaming to single chunk for now
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
        return Mono.error(new ProviderException("Bedrock embeddings not yet implemented", 501, ProviderType.BEDROCK));
    }

    @Override
    public ProviderType type() {
        return ProviderType.BEDROCK;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    private Map<String, Object> buildBedrockBody(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var body = new LinkedHashMap<String, Object>();
        var modelId = deployment.params().model().toLowerCase();

        if (modelId.contains("anthropic") || modelId.contains("claude")) {
            body.put("anthropic_version", "bedrock-2023-05-31");
            body.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 4096);

            var systemParts = new StringBuilder();
            var messages = new ArrayList<Map<String, Object>>();
            for (var msg : request.messages()) {
                if ("system".equals(msg.role())) {
                    if (!systemParts.isEmpty()) systemParts.append("\n\n");
                    systemParts.append(msg.contentAsString());
                } else {
                    messages.add(Map.of("role", msg.role(), "content", msg.contentAsString()));
                }
            }
            if (!systemParts.isEmpty()) body.put("system", systemParts.toString());
            body.put("messages", messages);
            if (request.temperature() != null) body.put("temperature", request.temperature());
        } else {
            // Generic Bedrock model format
            var messages = request.messages().stream()
                    .map(m -> Map.of("role", (Object) m.role(), "content", m.contentAsString()))
                    .toList();
            body.put("messages", messages);
            body.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 4096);
            if (request.temperature() != null) body.put("temperature", request.temperature());
        }
        return body;
    }

    private ChatCompletionResponse mapBedrockResponse(JsonNode response, String modelName) {
        var text = "";
        if (response.has("content")) {
            var content = response.get("content");
            if (content.isArray() && !content.isEmpty()) {
                text = content.get(0).path("text").asText("");
            }
        } else if (response.has("output")) {
            text = response.path("output").path("message").path("content").get(0).path("text").asText("");
        }

        var message = new ChatMessage("assistant", text, null, null, null);
        var choice = new ChatCompletionResponse.Choice(0, message, "stop");
        var inputTokens = response.path("usage").path("input_tokens").asInt(0);
        var outputTokens = response.path("usage").path("output_tokens").asInt(0);
        var usage = new UsageInfo(inputTokens, outputTokens, inputTokens + outputTokens);

        return new ChatCompletionResponse(
                "bedrock-" + UUID.randomUUID().toString().substring(0, 8),
                "chat.completion", Instant.now().getEpochSecond(),
                modelName, List.of(choice), usage, null
        );
    }
}
