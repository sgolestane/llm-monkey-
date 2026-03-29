package ai.llmmonkey.api.exception;

public final class ModelNotFoundException extends LlmMonkeyException {
    public ModelNotFoundException(String model) {
        super("Model not found: " + model, 404, "invalid_request_error", "model_not_found");
    }
}
