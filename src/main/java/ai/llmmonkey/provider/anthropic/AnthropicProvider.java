package ai.llmmonkey.provider.anthropic;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.api.exception.ProviderException;
import ai.llmmonkey.config.ModelDeploymentConfig;
import ai.llmmonkey.provider.EndpointType;
import ai.llmmonkey.provider.LlmProvider;
import ai.llmmonkey.provider.ProviderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public final class AnthropicProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Set<EndpointType> SUPPORTED = Set.of(EndpointType.CHAT_COMPLETION);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        var anthropicRequest = buildAnthropicRequest(request, deployment);

        return webClient.post()
                .uri(apiBase + "/v1/messages")
                .header("x-api-key", deployment.params().apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(anthropicRequest)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> mapToOpenAiResponse(response, request.model()))
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Anthropic API error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.ANTHROPIC));
    }

    @Override
    public Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var apiBase = resolveApiBase(deployment);
        var anthropicRequest = buildAnthropicRequest(request, deployment);
        ((ObjectNode) anthropicRequest).put("stream", true);

        return webClient.post()
                .uri(apiBase + "/v1/messages")
                .header("x-api-key", deployment.params().apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(anthropicRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .mapNotNull(line -> {
                    var data = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (data.isBlank() || data.startsWith("event:")) return null;
                    try {
                        return mapStreamEvent(objectMapper.readTree(data), request.model());
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse Anthropic stream: {}", data, e);
                        return null;
                    }
                })
                .onErrorMap(WebClientResponseException.class, e ->
                        new ProviderException("Anthropic streaming error: " + e.getResponseBodyAsString(),
                                e.getStatusCode().value(), ProviderType.ANTHROPIC));
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request, ModelDeploymentConfig deployment) {
        return Mono.error(new ProviderException("Anthropic does not support embeddings", 400, ProviderType.ANTHROPIC));
    }

    @Override
    public ProviderType type() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    private JsonNode buildAnthropicRequest(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        var node = objectMapper.createObjectNode();
        node.put("model", deployment.params().model());
        node.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 4096);

        if (request.temperature() != null) node.put("temperature", request.temperature());
        if (request.topP() != null) node.put("top_p", request.topP());

        // Extract system messages
        var systemParts = new StringBuilder();
        var messages = new ArrayList<ChatMessage>();
        for (var msg : request.messages()) {
            if ("system".equals(msg.role())) {
                if (!systemParts.isEmpty()) systemParts.append("\n\n");
                systemParts.append(msg.contentAsString());
            } else {
                messages.add(msg);
            }
        }
        if (!systemParts.isEmpty()) {
            node.put("system", systemParts.toString());
        }

        // Ensure alternating user/assistant messages
        var mergedMessages = mergeConsecutiveRoles(messages);
        var messagesArray = objectMapper.valueToTree(mergedMessages);
        node.set("messages", (ArrayNode) messagesArray);

        // Map tools
        if (request.tools() != null && !request.tools().isEmpty()) {
            var toolsArray = objectMapper.createArrayNode();
            for (var tool : request.tools()) {
                var toolNode = objectMapper.createObjectNode();
                toolNode.put("name", tool.function().name());
                if (tool.function().description() != null) {
                    toolNode.put("description", tool.function().description());
                }
                if (tool.function().parameters() != null) {
                    toolNode.set("input_schema", objectMapper.valueToTree(tool.function().parameters()));
                }
                toolsArray.add(toolNode);
            }
            node.set("tools", toolsArray);
        }

        return node;
    }

    private List<ChatMessage> mergeConsecutiveRoles(List<ChatMessage> messages) {
        if (messages.isEmpty()) return messages;
        var merged = new ArrayList<ChatMessage>();
        ChatMessage prev = null;
        for (var msg : messages) {
            if (prev != null && prev.role().equals(msg.role())) {
                var combined = prev.contentAsString() + "\n\n" + msg.contentAsString();
                prev = new ChatMessage(prev.role(), combined, prev.name(), prev.toolCalls(), prev.toolCallId());
            } else {
                if (prev != null) merged.add(prev);
                prev = msg;
            }
        }
        if (prev != null) merged.add(prev);
        return merged;
    }

    private ChatCompletionResponse mapToOpenAiResponse(JsonNode response, String modelName) {
        var id = response.path("id").asText("msg_" + UUID.randomUUID());
        var model = response.path("model").asText(modelName);

        var contentBlocks = response.path("content");
        var text = new StringBuilder();
        for (var block : contentBlocks) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }

        var finishReason = mapStopReason(response.path("stop_reason").asText("end_turn"));
        var message = new ChatMessage("assistant", text.toString(), null, null, null);
        var choice = new ChatCompletionResponse.Choice(0, message, finishReason);

        var inputTokens = response.path("usage").path("input_tokens").asInt(0);
        var outputTokens = response.path("usage").path("output_tokens").asInt(0);
        var usage = new UsageInfo(inputTokens, outputTokens, inputTokens + outputTokens);

        return new ChatCompletionResponse(id, "chat.completion", Instant.now().getEpochSecond(),
                model, List.of(choice), usage, null);
    }

    private StreamingChatChunk mapStreamEvent(JsonNode event, String modelName) {
        var type = event.path("type").asText("");
        return switch (type) {
            case "content_block_delta" -> {
                var delta = event.path("delta");
                var text = delta.path("text").asText("");
                yield new StreamingChatChunk(
                        "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8),
                        "chat.completion.chunk", Instant.now().getEpochSecond(), modelName,
                        List.of(new StreamingChatChunk.StreamingChoice(
                                0, new StreamingChatChunk.Delta(null, text, null), null)),
                        null);
            }
            case "message_start" -> {
                yield new StreamingChatChunk(
                        event.path("message").path("id").asText("chatcmpl-stream"),
                        "chat.completion.chunk", Instant.now().getEpochSecond(), modelName,
                        List.of(new StreamingChatChunk.StreamingChoice(
                                0, new StreamingChatChunk.Delta("assistant", "", null), null)),
                        null);
            }
            case "message_delta" -> {
                var stopReason = mapStopReason(event.path("delta").path("stop_reason").asText(""));
                var inputTokens = event.path("usage").path("input_tokens").asInt(0);
                var outputTokens = event.path("usage").path("output_tokens").asInt(0);
                yield new StreamingChatChunk(
                        "chatcmpl-stream", "chat.completion.chunk",
                        Instant.now().getEpochSecond(), modelName,
                        List.of(new StreamingChatChunk.StreamingChoice(
                                0, new StreamingChatChunk.Delta(null, null, null), stopReason)),
                        new UsageInfo(inputTokens, outputTokens, inputTokens + outputTokens));
            }
            default -> null;
        };
    }

    private String mapStopReason(String anthropicReason) {
        return switch (anthropicReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            default -> "stop";
        };
    }

    private String resolveApiBase(ModelDeploymentConfig deployment) {
        var base = deployment.params().apiBase();
        if (base == null || base.isBlank()) return "https://api.anthropic.com";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
