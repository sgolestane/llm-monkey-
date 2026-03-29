package ai.llmmonkey.api.controller;

import ai.llmmonkey.api.dto.ChatCompletionRequest;
import ai.llmmonkey.api.dto.ChatCompletionResponse;
import ai.llmmonkey.api.dto.StreamingChatChunk;
import ai.llmmonkey.router.Router;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
public class ChatCompletionController {

    private final Router router;
    private final ObjectMapper objectMapper;

    public ChatCompletionController(Router router, ObjectMapper objectMapper) {
        this.router = router;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat/completions")
    public Mono<?> chatCompletions(@RequestBody ChatCompletionRequest request) {
        if (Boolean.TRUE.equals(request.stream())) {
            return Mono.just(streamResponse(request));
        }
        return router.routeChatCompletion(request);
    }

    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            headers = "X-Stream=true")
    public Flux<ServerSentEvent<String>> chatCompletionsStream(@RequestBody ChatCompletionRequest request) {
        return streamResponse(request);
    }

    private Flux<ServerSentEvent<String>> streamResponse(ChatCompletionRequest request) {
        return router.routeChatCompletionStream(request)
                .map(chunk -> {
                    try {
                        return ServerSentEvent.builder(objectMapper.writeValueAsString(chunk)).build();
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").build()));
    }
}
