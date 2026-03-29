package ai.llmmonkey.auth;

import ai.llmmonkey.config.LlmMonkeyProperties;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@Component
@Order(1)
public class AuthenticationWebFilter implements WebFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/v1/models");

    private final VirtualKeyService virtualKeyService;
    private final LlmMonkeyProperties properties;

    public AuthenticationWebFilter(VirtualKeyService virtualKeyService, LlmMonkeyProperties properties) {
        this.virtualKeyService = virtualKeyService;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "{\"error\":\"Missing or invalid Authorization header\"}");
        }

        String token = authHeader.substring("Bearer ".length()).trim();

        // Check against master key
        String masterKey = properties.masterKey();
        if (masterKey != null && masterKey.equals(token)) {
            return chain.filter(exchange);
        }

        // Validate virtual key
        Optional<AuthContext> authContext = virtualKeyService.validateKey(token);
        if (authContext.isEmpty()) {
            return unauthorized(exchange, "{\"error\":\"Invalid API key\"}");
        }

        exchange.getAttributes().put("authContext", authContext.get());
        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String body) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }
}
