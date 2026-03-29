package ai.llmmonkey.router;

import ai.llmmonkey.api.dto.*;
import ai.llmmonkey.api.exception.ModelNotFoundException;
import ai.llmmonkey.api.exception.ProviderException;
import ai.llmmonkey.config.LlmMonkeyProperties;
import ai.llmmonkey.config.RouterSettingsConfig;
import ai.llmmonkey.provider.ProviderRegistry;
import ai.llmmonkey.provider.ProviderType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Component
public class Router {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    private final LlmMonkeyProperties properties;
    private final ProviderRegistry providerRegistry;
    private final DeploymentPool deploymentPool;
    private final RoutingStrategy routingStrategy;
    private final RouterSettingsConfig settings;

    public Router(LlmMonkeyProperties properties, ProviderRegistry providerRegistry) {
        this.properties = properties;
        this.providerRegistry = providerRegistry;
        this.deploymentPool = new DeploymentPool();
        this.settings = properties.routerSettings();
        this.routingStrategy = createStrategy(settings.routingStrategy());
    }

    @PostConstruct
    public void init() {
        for (var modelConfig : properties.modelList()) {
            deploymentPool.addDeployment(modelConfig);
            log.info("Registered deployment: {} -> {}/{}", modelConfig.modelName(),
                    modelConfig.provider(), modelConfig.params().model());
        }
        log.info("Router initialized with {} deployments, strategy: {}",
                properties.modelList().size(), routingStrategy.name());
    }

    public Mono<ChatCompletionResponse> routeChatCompletion(ChatCompletionRequest request) {
        return selectDeployment(request.model())
                .flatMap(deployment -> {
                    var provider = providerRegistry.getProvider(deployment.getConfig().provider());
                    deployment.startRequest();
                    var start = System.currentTimeMillis();
                    return provider.chatCompletion(request, deployment.getConfig())
                            .doOnSuccess(resp -> {
                                var tokens = resp.usage() != null ? resp.usage().totalTokens() : 0;
                                deployment.recordSuccess(System.currentTimeMillis() - start, tokens);
                            })
                            .doOnError(e -> deployment.recordFailure(
                                    settings.allowedFails(), settings.cooldownSeconds()));
                })
                .retryWhen(Retry.backoff(settings.numRetries(), Duration.ofMillis(100))
                        .filter(e -> isRetryable(e))
                        .doBeforeRetry(signal -> log.warn("Retrying request (attempt {}): {}",
                                signal.totalRetries() + 1, signal.failure().getMessage())));
    }

    public Flux<StreamingChatChunk> routeChatCompletionStream(ChatCompletionRequest request) {
        return selectDeployment(request.model())
                .flatMapMany(deployment -> {
                    var provider = providerRegistry.getProvider(deployment.getConfig().provider());
                    deployment.startRequest();
                    var start = System.currentTimeMillis();
                    return provider.chatCompletionStream(request, deployment.getConfig())
                            .doOnComplete(() -> deployment.recordSuccess(System.currentTimeMillis() - start, 0))
                            .doOnError(e -> deployment.recordFailure(
                                    settings.allowedFails(), settings.cooldownSeconds()));
                });
    }

    public Mono<EmbeddingResponse> routeEmbedding(EmbeddingRequest request) {
        return selectDeployment(request.model())
                .flatMap(deployment -> {
                    var provider = providerRegistry.getProvider(deployment.getConfig().provider());
                    deployment.startRequest();
                    var start = System.currentTimeMillis();
                    return provider.embedding(request, deployment.getConfig())
                            .doOnSuccess(resp -> deployment.recordSuccess(System.currentTimeMillis() - start, 0))
                            .doOnError(e -> deployment.recordFailure(
                                    settings.allowedFails(), settings.cooldownSeconds()));
                });
    }

    public List<String> getAvailableModels() {
        return deploymentPool.getAvailableModels();
    }

    public DeploymentPool getDeploymentPool() {
        return deploymentPool;
    }

    private Mono<DeploymentState> selectDeployment(String modelName) {
        var healthy = deploymentPool.getHealthyDeployments(modelName);
        if (healthy.isEmpty()) {
            if (!deploymentPool.hasModel(modelName)) {
                return Mono.error(new ModelNotFoundException(modelName));
            }
            return Mono.error(new ProviderException(
                    "All deployments for model '" + modelName + "' are in cooldown",
                    503, ProviderType.OPENAI));
        }
        return Mono.just(routingStrategy.select(healthy));
    }

    private RoutingStrategy createStrategy(String name) {
        return switch (name.toLowerCase()) {
            case "round-robin", "round_robin", "simple-shuffle" -> new RoundRobinStrategy();
            case "least-busy", "least_busy" -> new LeastBusyStrategy();
            case "latency-based", "latency_based" -> new LatencyBasedStrategy();
            case "cost-based", "cost_based" -> new CostBasedStrategy(null); // Will be injected later
            case "usage-based", "usage_based" -> new UsageBasedStrategy();
            default -> new RoundRobinStrategy();
        };
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof ProviderException pe) {
            return pe.getStatusCode() == 429 || pe.getStatusCode() >= 500;
        }
        return false;
    }
}
