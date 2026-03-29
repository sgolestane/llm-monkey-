package ai.llmmonkey.ratelimit;

import ai.llmmonkey.api.exception.RateLimitException;
import ai.llmmonkey.auth.AuthContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
public class RateLimitService {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;

    public RateLimitService(List<RateLimiter> rateLimiters) {
        // Prefer Redis rate limiter if available, fall back to in-memory
        this.rateLimiter = rateLimiters.stream()
                .filter(rl -> rl instanceof RedisRateLimiter)
                .findFirst()
                .orElseGet(() -> rateLimiters.stream()
                        .filter(rl -> rl instanceof InMemoryRateLimiter)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No RateLimiter bean available")));
    }

    public Mono<Void> checkRateLimits(AuthContext ctx) {
        Mono<Void> rpmCheck = Mono.empty();
        Mono<Void> tpmCheck = Mono.empty();

        if (ctx.rpmLimit() != null && ctx.rpmLimit() > 0) {
            String rpmKey = "rpm:" + ctx.apiKeyHash();
            rpmCheck = rateLimiter.checkRateLimit(rpmKey, ctx.rpmLimit(), ONE_MINUTE)
                    .flatMap(result -> {
                        if (!result.allowed()) {
                            long retryAfter = result.resetAtEpochSecond() - java.time.Instant.now().getEpochSecond();
                            return Mono.error(new RateLimitException(
                                    "Rate limit exceeded: " + ctx.rpmLimit() + " requests per minute",
                                    Math.max(1, retryAfter)));
                        }
                        return Mono.empty();
                    });
        }

        if (ctx.tpmLimit() != null && ctx.tpmLimit() > 0) {
            String tpmKey = "tpm:" + ctx.apiKeyHash();
            tpmCheck = rateLimiter.checkRateLimit(tpmKey, ctx.tpmLimit(), ONE_MINUTE)
                    .flatMap(result -> {
                        if (!result.allowed()) {
                            long retryAfter = result.resetAtEpochSecond() - java.time.Instant.now().getEpochSecond();
                            return Mono.error(new RateLimitException(
                                    "Rate limit exceeded: " + ctx.tpmLimit() + " tokens per minute",
                                    Math.max(1, retryAfter)));
                        }
                        return Mono.empty();
                    });
        }

        return rpmCheck.then(tpmCheck);
    }
}
