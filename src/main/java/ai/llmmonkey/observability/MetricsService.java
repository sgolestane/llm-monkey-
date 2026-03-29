package ai.llmmonkey.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicInteger> activeRequestGauges = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String model, String provider, String status, long durationMs,
                              long promptTokens, long completionTokens, double cost,
                              List<String> tags) {
        recordRequest(model, provider, status, durationMs, promptTokens, completionTokens, cost);

        // Record per-tag metrics
        if (tags != null) {
            for (String tag : tags) {
                Counter.builder("llm_monkey_tag_requests_total")
                        .tag("tag", tag)
                        .tag("model", model)
                        .register(meterRegistry)
                        .increment();

                Counter.builder("llm_monkey_tag_spend_total")
                        .tag("tag", tag)
                        .register(meterRegistry)
                        .increment(cost);

                Counter.builder("llm_monkey_tag_tokens_total")
                        .tag("tag", tag)
                        .tag("direction", "input")
                        .register(meterRegistry)
                        .increment(promptTokens);

                Counter.builder("llm_monkey_tag_tokens_total")
                        .tag("tag", tag)
                        .tag("direction", "output")
                        .register(meterRegistry)
                        .increment(completionTokens);
            }
        }
    }

    public void recordRequest(String model, String provider, String status, long durationMs,
                              long promptTokens, long completionTokens, double cost) {
        // Request counter
        Counter.builder("llm_monkey_requests_total")
                .tag("model", model)
                .tag("provider", provider)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        // Request duration
        Timer.builder("llm_monkey_request_duration_seconds")
                .tag("model", model)
                .tag("provider", provider)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        // Input tokens
        Counter.builder("llm_monkey_tokens_total")
                .tag("model", model)
                .tag("provider", provider)
                .tag("direction", "input")
                .register(meterRegistry)
                .increment(promptTokens);

        // Output tokens
        Counter.builder("llm_monkey_tokens_total")
                .tag("model", model)
                .tag("provider", provider)
                .tag("direction", "output")
                .register(meterRegistry)
                .increment(completionTokens);

        // Spend
        Counter.builder("llm_monkey_spend_total")
                .tag("model", model)
                .tag("provider", provider)
                .register(meterRegistry)
                .increment(cost);
    }

    public void incrementActiveRequests(String model) {
        AtomicInteger gauge = activeRequestGauges.computeIfAbsent(model, m -> {
            AtomicInteger counter = new AtomicInteger(0);
            meterRegistry.gauge("llm_monkey_active_requests", io.micrometer.core.instrument.Tags.of("model", m), counter);
            return counter;
        });
        gauge.incrementAndGet();
    }

    public void decrementActiveRequests(String model) {
        AtomicInteger gauge = activeRequestGauges.get(model);
        if (gauge != null) {
            gauge.decrementAndGet();
        }
    }
}
