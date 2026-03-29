package ai.llmmonkey.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
public class TwoLevelCacheService implements CacheService {

    private final Cache<String, String> l1Cache;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Duration l2DefaultTtl;

    public TwoLevelCacheService(
            @Nullable ReactiveStringRedisTemplate redisTemplate,
            @Value("${llm-monkey.cache.l2-ttl:PT1H}") Duration l2DefaultTtl) {
        this.redisTemplate = redisTemplate;
        this.l2DefaultTtl = l2DefaultTtl;
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public Mono<Optional<String>> get(String key) {
        // Check L1 first
        String l1Value = l1Cache.getIfPresent(key);
        if (l1Value != null) {
            return Mono.just(Optional.of(l1Value));
        }

        // Check L2 (Redis) on miss
        if (redisTemplate != null) {
            return redisTemplate.opsForValue().get(key)
                    .map(value -> {
                        // Promote to L1
                        l1Cache.put(key, value);
                        return Optional.of(value);
                    })
                    .defaultIfEmpty(Optional.empty());
        }

        return Mono.just(Optional.empty());
    }

    @Override
    public Mono<Void> put(String key, String value, Duration ttl) {
        // Write to L1
        l1Cache.put(key, value);

        // Write to L2 (Redis) if available
        if (redisTemplate != null) {
            Duration redisTtl = (ttl != null) ? ttl : l2DefaultTtl;
            return redisTemplate.opsForValue().set(key, value, redisTtl).then();
        }

        return Mono.empty();
    }
}
