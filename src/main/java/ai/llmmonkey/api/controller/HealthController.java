package ai.llmmonkey.api.controller;

import ai.llmmonkey.router.Router;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final Router router;

    public HealthController(Router router) {
        this.router = router;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "healthy",
                "timestamp", Instant.now().toString(),
                "version", "0.1.0"
        );
    }

    @GetMapping("/health/readiness")
    public Map<String, Object> readiness() {
        var models = router.getAvailableModels();
        return Map.of(
                "status", models.isEmpty() ? "degraded" : "ready",
                "available_models", models.size(),
                "timestamp", Instant.now().toString()
        );
    }
}
