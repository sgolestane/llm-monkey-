package ai.llmmonkey.router;

import ai.llmmonkey.config.ModelDeploymentConfig;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DeploymentState {

    private final ModelDeploymentConfig config;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    private volatile Instant cooldownUntil = Instant.EPOCH;
    private volatile double avgLatencyMs = 0;

    public DeploymentState(ModelDeploymentConfig config) {
        this.config = config;
    }

    public ModelDeploymentConfig getConfig() { return config; }

    public boolean isHealthy() {
        return cooldownUntil.isBefore(Instant.now());
    }

    public void recordSuccess(long latencyMs, int tokens) {
        consecutiveFailures.set(0);
        activeRequests.decrementAndGet();
        totalRequests.incrementAndGet();
        totalTokens.addAndGet(tokens);
        // Exponential moving average
        avgLatencyMs = avgLatencyMs * 0.8 + latencyMs * 0.2;
    }

    public void recordFailure(int allowedFails, int cooldownSeconds) {
        activeRequests.decrementAndGet();
        if (consecutiveFailures.incrementAndGet() >= allowedFails) {
            cooldownUntil = Instant.now().plusSeconds(cooldownSeconds);
        }
    }

    public void startRequest() {
        activeRequests.incrementAndGet();
    }

    public int getActiveRequests() { return activeRequests.get(); }
    public long getTotalRequests() { return totalRequests.get(); }
    public long getTotalTokens() { return totalTokens.get(); }
    public double getAvgLatencyMs() { return avgLatencyMs; }
    public Instant getCooldownUntil() { return cooldownUntil; }
}
