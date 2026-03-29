package ai.llmmonkey.ratelimit;

import reactor.core.publisher.Mono;

import java.time.Duration;

public interface RateLimiter {

    Mono<RateLimitResult> checkRateLimit(String key, long limit, Duration window);
}
