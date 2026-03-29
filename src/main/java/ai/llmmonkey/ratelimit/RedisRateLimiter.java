package ai.llmmonkey.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisRateLimiter implements RateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<RateLimitResult> checkRateLimit(String key, long limit, Duration window) {
        String redisKey = "ratelimit:" + key + ":" + window.getSeconds();
        long now = Instant.now().toEpochMilli();
        double nowScore = (double) now;
        double windowStart = (double) (now - window.toMillis());
        String member = now + ":" + Math.random();

        ReactiveZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        return zSetOps.removeRangeByScore(redisKey, Range.closed(0.0, windowStart))
                .then(zSetOps.add(redisKey, member, nowScore))
                .then(redisTemplate.expire(redisKey, window.plusSeconds(10)))
                .then(zSetOps.count(redisKey, Range.closed(windowStart, Double.MAX_VALUE)))
                .map(count -> {
                    boolean allowed = count <= limit;
                    long remaining = Math.max(0, limit - count);
                    long resetAt = Instant.now().plus(window).getEpochSecond();
                    if (!allowed) {
                        // Remove the member we just added since request is denied
                        zSetOps.remove(redisKey, member).subscribe();
                        remaining = 0;
                    }
                    return new RateLimitResult(allowed, remaining, resetAt);
                });
    }
}
