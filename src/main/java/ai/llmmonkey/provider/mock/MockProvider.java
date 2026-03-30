package ai.llmmonkey.provider.mock;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.api.exception.ProviderException;
import ai.llmmonkey.config.ModelDeploymentConfig;
import ai.llmmonkey.provider.EndpointType;
import ai.llmmonkey.provider.LlmProvider;
import ai.llmmonkey.provider.ProviderType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mock LLM provider for testing. Returns deterministic, configurable responses
 * without making any external HTTP calls.
 */
@Component
public class MockProvider implements LlmProvider {

    private static final Set<EndpointType> SUPPORTED = Set.of(
            EndpointType.CHAT_COMPLETION, EndpointType.COMPLETION,
            EndpointType.EMBEDDING
    );

    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private volatile Duration latency = Duration.ZERO;
    private volatile boolean failNextRequest = false;

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        return Mono.defer(() -> {
            if (failNextRequest) {
                failNextRequest = false;
                return Mono.error(new ProviderException("Mock provider simulated failure", 500, ProviderType.MOCK));
            }

            int reqNum = requestCounter.incrementAndGet();
            String lastUserMessage = extractLastUserMessage(request);

            var response = new ChatCompletionResponse(
                    "chatcmpl-mock-" + reqNum,
                    "chat.completion",
                    Instant.now().getEpochSecond(),
                    deployment.params().model(),
                    List.of(new ChatCompletionResponse.Choice(
                            0,
                            new ChatMessage("assistant", "Mock response to: " + lastUserMessage, null, null, null),
                            "stop"
                    )),
                    new UsageInfo(10, 15, 25),
                    "mock-fp-001"
            );

            return Mono.just(response);
        }).delayElement(latency);
    }

    @Override
    public Flux<StreamingChatChunk> chatCompletionStream(ChatCompletionRequest request, ModelDeploymentConfig deployment) {
        int reqNum = requestCounter.incrementAndGet();
        String lastUserMessage = extractLastUserMessage(request);
        String fullResponse = "Mock streamed response to: " + lastUserMessage;
        String[] words = fullResponse.split(" ");

        return Flux.fromArray(words)
                .index()
                .map(tuple -> {
                    long idx = tuple.getT1();
                    String word = tuple.getT2();
                    boolean isLast = idx == words.length - 1;
                    String content = idx == 0 ? word : " " + word;

                    return new StreamingChatChunk(
                            "chatcmpl-mock-stream-" + reqNum,
                            "chat.completion.chunk",
                            Instant.now().getEpochSecond(),
                            deployment.params().model(),
                            List.of(new StreamingChatChunk.StreamingChoice(
                                    0,
                                    new StreamingChatChunk.Delta(
                                            idx == 0 ? "assistant" : null,
                                            content,
                                            null
                                    ),
                                    isLast ? "stop" : null
                            )),
                            isLast ? new UsageInfo(10, words.length, 10 + words.length) : null
                    );
                })
                .delayElements(latency);
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request, ModelDeploymentConfig deployment) {
        return Mono.defer(() -> {
            requestCounter.incrementAndGet();

            var embeddingData = new EmbeddingResponse.EmbeddingData(
                    "embedding",
                    0,
                    List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8)
            );

            var response = new EmbeddingResponse(
                    "list",
                    List.of(embeddingData),
                    deployment.params().model(),
                    new UsageInfo(8, 0, 8)
            );

            return Mono.just(response);
        }).delayElement(latency);
    }

    @Override
    public ProviderType type() {
        return ProviderType.MOCK;
    }

    @Override
    public Set<EndpointType> supportedEndpoints() {
        return SUPPORTED;
    }

    // --- Test control methods ---

    public int getRequestCount() {
        return requestCounter.get();
    }

    public void resetRequestCount() {
        requestCounter.set(0);
    }

    public void setLatency(Duration latency) {
        this.latency = latency;
    }

    public void setFailNextRequest(boolean fail) {
        this.failNextRequest = fail;
    }

    public void reset() {
        requestCounter.set(0);
        latency = Duration.ZERO;
        failNextRequest = false;
    }

    private String extractLastUserMessage(ChatCompletionRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            return "<empty>";
        }
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            var msg = request.messages().get(i);
            if ("user".equals(msg.role())) {
                return msg.contentAsString();
            }
        }
        return request.messages().getLast().contentAsString();
    }
}
