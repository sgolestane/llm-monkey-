package ai.llmmonkey.api.controller;

import ai.llmmonkey.api.dto.EmbeddingRequest;
import ai.llmmonkey.api.dto.EmbeddingResponse;
import ai.llmmonkey.router.Router;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
public class EmbeddingController {

    private final Router router;

    public EmbeddingController(Router router) {
        this.router = router;
    }

    @PostMapping("/embeddings")
    public Mono<EmbeddingResponse> embeddings(@RequestBody EmbeddingRequest request) {
        return router.routeEmbedding(request);
    }
}
