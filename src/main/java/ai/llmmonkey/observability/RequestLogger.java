package ai.llmmonkey.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RequestLogger {

    private static final Logger log = LoggerFactory.getLogger(RequestLogger.class);

    public void logRequest(String requestId, String model, String provider, long latencyMs,
                           long promptTokens, long completionTokens, double cost) {
        log.info("request_id={} model={} provider={} latency_ms={} prompt_tokens={} completion_tokens={} cost={}",
                requestId, model, provider, latencyMs, promptTokens, completionTokens, cost);
    }

    public void logError(String requestId, String model, String provider, long latencyMs, Throwable error) {
        log.error("request_id={} model={} provider={} latency_ms={} error={}",
                requestId, model, provider, latencyMs, error.getMessage(), error);
    }
}
