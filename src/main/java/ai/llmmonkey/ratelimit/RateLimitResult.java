package ai.llmmonkey.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long remaining,
        long resetAtEpochSecond
) {
}
