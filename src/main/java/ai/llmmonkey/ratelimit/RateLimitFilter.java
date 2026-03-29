package ai.llmmonkey.ratelimit;

import ai.llmmonkey.auth.AuthContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Order(2)
public class RateLimitFilter implements WebFilter {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final RateLimitService rateLimitService;
    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimitService rateLimitService, java.util.List<RateLimiter> rateLimiters) {
        this.rateLimitService = rateLimitService;
        this.rateLimiter = rateLimiters.stream()
                .filter(rl -> rl instanceof RedisRateLimiter)
                .findFirst()
                .orElseGet(() -> rateLimiters.stream()
                        .filter(rl -> rl instanceof InMemoryRateLimiter)
                        .findFirst()
                        .orElse(null));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AuthContext ctx = exchange.getAttribute("authContext");
        if (ctx == null) {
            return chain.filter(exchange);
        }

        return rateLimitService.checkRateLimits(ctx)
                .then(Mono.defer(() -> addRateLimitHeaders(exchange, ctx)))
                .then(chain.filter(exchange));
    }

    private Mono<Void> addRateLimitHeaders(ServerWebExchange exchange, AuthContext ctx) {
        ServerHttpResponse response = exchange.getResponse();

        Mono<Void> rpmHeaders = Mono.empty();
        Mono<Void> tpmHeaders = Mono.empty();

        if (ctx.rpmLimit() != null && ctx.rpmLimit() > 0) {
            String rpmKey = "rpm:" + ctx.apiKeyHash();
            rpmHeaders = rateLimiter.checkRateLimit(rpmKey, ctx.rpmLimit(), ONE_MINUTE)
                    .doOnNext(result -> {
                        response.getHeaders().set("x-ratelimit-limit-requests", String.valueOf(ctx.rpmLimit()));
                        response.getHeaders().set("x-ratelimit-remaining-requests", String.valueOf(result.remaining()));
                    })
                    .then();
        }

        if (ctx.tpmLimit() != null && ctx.tpmLimit() > 0) {
            String tpmKey = "tpm:" + ctx.apiKeyHash();
            tpmHeaders = rateLimiter.checkRateLimit(tpmKey, ctx.tpmLimit(), ONE_MINUTE)
                    .doOnNext(result -> {
                        response.getHeaders().set("x-ratelimit-limit-tokens", String.valueOf(ctx.tpmLimit()));
                        response.getHeaders().set("x-ratelimit-remaining-tokens", String.valueOf(result.remaining()));
                    })
                    .then();
        }

        return rpmHeaders.then(tpmHeaders);
    }
}
