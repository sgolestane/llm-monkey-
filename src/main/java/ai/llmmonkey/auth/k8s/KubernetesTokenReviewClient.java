package ai.llmmonkey.auth.k8s;

import ai.llmmonkey.config.K8sAuthConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "llm-monkey.k8s-auth", name = "enabled", havingValue = "true")
public class KubernetesTokenReviewClient {

    private static final Logger log = LoggerFactory.getLogger(KubernetesTokenReviewClient.class);
    private static final String TOKEN_REVIEW_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String audience;
    private final Path serviceAccountTokenPath;
    private final Cache<String, TokenReviewResult> cache;

    public KubernetesTokenReviewClient(K8sAuthConfig config, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.audience = config.audience();
        this.serviceAccountTokenPath = Path.of(config.serviceAccountTokenPath());

        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(config.tokenCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();

        this.webClient = buildWebClient(config);
    }

    public Mono<TokenReviewResult> validate(String callerToken) {
        String cacheKey = hashToken(callerToken);
        TokenReviewResult cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached);
        }

        return readProviderToken()
                .flatMap(providerToken -> callTokenReview(callerToken, providerToken))
                .doOnNext(result -> {
                    if (result.authenticated()) {
                        cache.put(cacheKey, result);
                    }
                })
                .doOnError(e -> log.error("TokenReview API call failed: {}", e.getMessage()));
    }

    private Mono<String> readProviderToken() {
        return Mono.fromCallable(() -> Files.readString(serviceAccountTokenPath, StandardCharsets.UTF_8).trim())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<TokenReviewResult> callTokenReview(String callerToken, String providerToken) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("apiVersion", "authentication.k8s.io/v1");
        body.put("kind", "TokenReview");
        ObjectNode spec = body.putObject("spec");
        spec.put("token", callerToken);
        if (audience != null && !audience.isBlank()) {
            ArrayNode audiences = spec.putArray("audiences");
            audiences.add(audience);
        }

        return webClient.post()
                .uri(TOKEN_REVIEW_PATH)
                .header("Authorization", "Bearer " + providerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse);
    }

    private TokenReviewResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode status = root.get("status");
            if (status == null) {
                log.warn("TokenReview response missing status field");
                return TokenReviewResult.unauthenticated();
            }

            boolean authenticated = status.has("authenticated") && status.get("authenticated").asBoolean(false);
            if (!authenticated) {
                return TokenReviewResult.unauthenticated();
            }

            JsonNode user = status.get("user");
            if (user == null || !user.has("username")) {
                log.warn("TokenReview authenticated but missing user.username");
                return TokenReviewResult.unauthenticated();
            }

            String username = user.get("username").asText();
            return TokenReviewResult.fromUsername(username);
        } catch (Exception e) {
            log.error("Failed to parse TokenReview response: {}", e.getMessage());
            return TokenReviewResult.unauthenticated();
        }
    }

    private WebClient buildWebClient(K8sAuthConfig config) {
        try {
            File caCertFile = new File(config.caCertPath());
            HttpClient httpClient;

            if (caCertFile.exists()) {
                SslContext sslContext = SslContextBuilder.forClient()
                        .trustManager(caCertFile)
                        .build();
                httpClient = HttpClient.create()
                        .secure(spec -> spec.sslContext(sslContext));
            } else {
                log.warn("K8s CA cert not found at {}; using default trust store", config.caCertPath());
                httpClient = HttpClient.create();
            }

            httpClient = httpClient
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .responseTimeout(Duration.ofSeconds(10));

            return WebClient.builder()
                    .baseUrl(config.apiServerUrl())
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (SSLException e) {
            throw new IllegalStateException("Failed to configure TLS for Kubernetes API client", e);
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
