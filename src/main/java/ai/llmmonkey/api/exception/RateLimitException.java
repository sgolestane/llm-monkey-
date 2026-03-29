package ai.llmmonkey.api.exception;

public final class RateLimitException extends LlmMonkeyException {

    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(message, 429, "rate_limit_error", "rate_limit_exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
