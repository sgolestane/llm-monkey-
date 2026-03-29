package ai.llmmonkey.api.controller;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.router.Router;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class CompletionController {

    private final Router router;

    public CompletionController(Router router) {
        this.router = router;
    }

    @PostMapping("/completions")
    public Mono<ChatCompletionResponse> completions(@RequestBody CompletionRequest request) {
        // Convert legacy completion to chat completion format
        var prompt = request.prompt() instanceof String s ? s : request.prompt().toString();
        var messages = List.of(new ChatMessage("user", prompt, null, null, null));
        var chatRequest = new ChatCompletionRequest(
                request.model(), messages, request.temperature(), request.topP(),
                request.n(), false, request.stop(), request.maxTokens(),
                request.presencePenalty(), request.frequencyPenalty(),
                request.user(), null, null, null, request.seed(), null, null, null, null
        );
        return router.routeChatCompletion(chatRequest);
    }
}
