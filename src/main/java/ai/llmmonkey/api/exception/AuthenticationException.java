package ai.llmmonkey.api.exception;

public final class AuthenticationException extends LlmMonkeyException {
    public AuthenticationException(String message) {
        super(message, 401, "authentication_error", "invalid_api_key");
    }
}
