package ai.llmmonkey.api.controller;

import ai.llmmonkey.api.dto.ModelListResponse;
import ai.llmmonkey.router.Router;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/v1")
public class ModelListController {

    private final Router router;

    public ModelListController(Router router) {
        this.router = router;
    }

    @GetMapping("/models")
    public ModelListResponse listModels() {
        var models = router.getAvailableModels().stream()
                .map(name -> new ModelListResponse.ModelData(name, "model",
                        Instant.now().getEpochSecond(), "llm-monkey"))
                .toList();
        return new ModelListResponse("list", models);
    }
}
