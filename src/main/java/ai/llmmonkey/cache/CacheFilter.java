package ai.llmmonkey.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Component
@Order(3)
public class CacheFilter implements WebFilter {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final CacheService cacheService;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final ObjectMapper objectMapper;

    public CacheFilter(CacheService cacheService, CacheKeyGenerator cacheKeyGenerator, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.cacheKeyGenerator = cacheKeyGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (!CHAT_COMPLETIONS_PATH.equals(path) || !HttpMethod.POST.equals(method)) {
            return chain.filter(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String body = new String(bytes, StandardCharsets.UTF_8);

                    try {
                        JsonNode json = objectMapper.readTree(body);

                        // Only cache non-streaming requests with temperature=0
                        boolean isStreaming = json.has("stream") && json.get("stream").asBoolean(false);
                        double temperature = json.has("temperature") ? json.get("temperature").asDouble(1.0) : 1.0;

                        if (isStreaming || temperature != 0.0) {
                            return chain.filter(withCachedBody(exchange, bytes));
                        }

                        String model = json.has("model") ? json.get("model").asText() : "";
                        JsonNode messages = json.get("messages");
                        String cacheKey = cacheKeyGenerator.generateKey(model, messages, json);

                        return cacheService.get(cacheKey)
                                .flatMap(cached -> {
                                    if (cached.isPresent()) {
                                        return writeCachedResponse(exchange, cached.get());
                                    }
                                    return chain.filter(withResponseCapture(exchange, bytes, cacheKey));
                                });
                    } catch (Exception e) {
                        return chain.filter(withCachedBody(exchange, bytes));
                    }
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> writeCachedResponse(ServerWebExchange exchange, String cachedBody) {
        byte[] responseBytes = cachedBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(responseBytes.length);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private ServerWebExchange withCachedBody(ServerWebExchange exchange, byte[] body) {
        ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
                return Flux.just(buffer);
            }
        };
        return exchange.mutate().request(decoratedRequest).build();
    }

    private ServerWebExchange withResponseCapture(ServerWebExchange exchange, byte[] requestBody, String cacheKey) {
        ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(requestBody);
                return Flux.just(buffer);
            }
        };

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            String responseBody = new String(bytes, StandardCharsets.UTF_8);

                            // Only cache successful responses
                            var statusCode = getStatusCode();
                            if (statusCode != null && statusCode.is2xxSuccessful()) {
                                cacheService.put(cacheKey, responseBody, CACHE_TTL).subscribe();
                            }

                            DataBuffer wrappedBuffer = getDelegate().bufferFactory().wrap(bytes);
                            return getDelegate().writeWith(Mono.just(wrappedBuffer));
                        });
            }
        };

        return exchange.mutate().request(decoratedRequest).response(decoratedResponse).build();
    }
}
