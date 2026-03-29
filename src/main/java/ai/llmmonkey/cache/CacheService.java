package ai.llmmonkey.cache;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

public interface CacheService {

    Mono<Optional<String>> get(String key);

    Mono<Void> put(String key, String value, Duration ttl);
}
