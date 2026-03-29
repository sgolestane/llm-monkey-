package ai.llmmonkey.auth;

import ai.llmmonkey.auth.k8s.K8sAuthorizationPolicy;
import ai.llmmonkey.auth.k8s.KubernetesTokenReviewClient;
import ai.llmmonkey.auth.k8s.TokenReviewResult;
import ai.llmmonkey.config.LlmMonkeyProperties;
import ai.llmmonkey.model.UserRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    private static final Logger log = LoggerFactory.getLogger(AuthenticationWebFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/health", "/health/readiness", "/actuator/prometheus"
    );

    private final VirtualKeyService virtualKeyService;
    private final JwtValidator jwtValidator;
    private final LlmMonkeyProperties properties;
    private final Optional<KubernetesTokenReviewClient> k8sTokenReviewClient;
    private final Optional<K8sAuthorizationPolicy> k8sAuthorizationPolicy;

    public AuthenticationWebFilter(
            VirtualKeyService virtualKeyService,
            JwtValidator jwtValidator,
            LlmMonkeyProperties properties,
            Optional<KubernetesTokenReviewClient> k8sTokenReviewClient,
            Optional<K8sAuthorizationPolicy> k8sAuthorizationPolicy) {
        this.virtualKeyService = virtualKeyService;
        this.jwtValidator = jwtValidator;
        this.properties = properties;
        this.k8sTokenReviewClient = k8sTokenReviewClient;
        this.k8sAuthorizationPolicy = k8sAuthorizationPolicy;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring("Bearer ".length()).trim();

        // 1. Master key check
        String masterKey = properties.masterKey();
        if (masterKey != null && !masterKey.isBlank() && masterKey.equals(token)) {
            return chain.filter(exchange);
        }

        // 2. Virtual key check (sk- prefix)
        if (token.startsWith("sk-")) {
            Optional<AuthContext> authContext = virtualKeyService.validateKey(token);
            if (authContext.isPresent()) {
                exchange.getAttributes().put("authContext", authContext.get());
                return chain.filter(exchange);
            }
            return unauthorized(exchange, "Invalid API key");
        }

        // 3. K8s ServiceAccount token check (eyJ prefix, when enabled)
        if (token.startsWith("eyJ") && k8sTokenReviewClient.isPresent() && k8sAuthorizationPolicy.isPresent()) {
            String requestPath = path;
            String httpMethod = Optional.ofNullable(exchange.getRequest().getMethod())
                    .map(HttpMethod::name)
                    .orElse("GET");

            return k8sTokenReviewClient.get().validate(token)
                    .flatMap(result -> handleK8sResult(exchange, chain, result, requestPath, httpMethod))
                    .onErrorResume(e -> {
                        log.error("K8s TokenReview failed with error: {}", e.getMessage());
                        return serviceUnavailable(exchange, "Authentication service unavailable");
                    });
        }

        // 4. SSO/OIDC JWT check
        if (token.startsWith("eyJ")) {
            Optional<AuthContext> authContext = jwtValidator.validate(token);
            if (authContext.isPresent()) {
                exchange.getAttributes().put("authContext", authContext.get());
                return chain.filter(exchange);
            }
        }

        return unauthorized(exchange, "Invalid API key");
    }

    private Mono<Void> handleK8sResult(
            ServerWebExchange exchange, WebFilterChain chain,
            TokenReviewResult result, String path, String httpMethod) {

        if (!result.authenticated()) {
            // Token not recognized by K8s -- fall through to SSO JWT
            Optional<AuthContext> jwtContext = jwtValidator.validate(
                    exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)
                            .substring("Bearer ".length()).trim());
            if (jwtContext.isPresent()) {
                exchange.getAttributes().put("authContext", jwtContext.get());
                return chain.filter(exchange);
            }
            return unauthorized(exchange, "Invalid API key");
        }

        // Authenticated by K8s -- check authorization
        boolean allowed = k8sAuthorizationPolicy.get()
                .isAllowed(result.namespace(), result.serviceAccountName(), path, httpMethod);

        if (!allowed) {
            log.info("K8s auth denied: caller={} path={} method={}",
                    result.username(), path, httpMethod);
            return forbidden(exchange, "Caller " + result.username() + " is not authorized for " + httpMethod + " " + path);
        }

        log.info("K8s auth allowed: caller={} path={} method={}",
                result.username(), path, httpMethod);

        AuthContext ctx = new AuthContext(
                null,                          // apiKeyHash
                result.username(),             // userId
                null,                          // teamId
                null,                          // organizationId
                UserRole.TEAM_MEMBER,          // role
                null,                          // maxBudget
                null,                          // tpmLimit
                null,                          // rpmLimit
                result.namespace(),            // k8sNamespace
                result.serviceAccountName()    // k8sServiceAccount
        );
        exchange.getAttributes().put("authContext", ctx);
        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, message);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, message);
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":{\"message\":\"" + message + "\",\"type\":\"" +
                status.getReasonPhrase().toLowerCase().replace(' ', '_') + "\",\"code\":" + status.value() + "}}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }
}
