package ai.llmmonkey.ratelimit;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, List<Long>> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<RateLimitResult> checkRateLimit(String key, long limit, Duration window) {
        return Mono.fromSupplier(() -> {
            long now = Instant.now().toEpochMilli();
            long windowMillis = window.toMillis();
            long windowStart = now - windowMillis;

            List<Long> timestamps = windows.compute(key, (k, existing) -> {
                List<Long> list = (existing != null) ? existing : new ArrayList<>();
                list.removeIf(ts -> ts < windowStart);
                return list;
            });

            synchronized (timestamps) {
                // Remove expired entries
                timestamps.removeIf(ts -> ts < windowStart);

                long count = timestamps.size();
                if (count < limit) {
                    timestamps.add(now);
                    long remaining = limit - count - 1;
                    long resetAt = Instant.now().plusMillis(windowMillis).getEpochSecond();
                    return new RateLimitResult(true, remaining, resetAt);
                } else {
                    // Find the earliest timestamp to determine reset time
                    long earliest = timestamps.stream().mapToLong(Long::longValue).min().orElse(now);
                    long resetAt = (earliest + windowMillis) / 1000;
                    long remaining = 0;
                    return new RateLimitResult(false, remaining, resetAt);
                }
            }
        });
    }
}
