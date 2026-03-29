package ai.llmmonkey.api.exception;

public final class AuthorizationException extends LlmMonkeyException {
    public AuthorizationException(String message) {
        super(message, 403, "authorization_error", "insufficient_permissions");
    }
}
