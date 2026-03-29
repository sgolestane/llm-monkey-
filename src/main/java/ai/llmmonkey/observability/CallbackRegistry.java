package ai.llmmonkey.observability;

import ai.llmmonkey.api.dto.ChatCompletionRequest;
import ai.llmmonkey.api.dto.ChatCompletionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class CallbackRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallbackRegistry.class);

    private final List<Callback> callbacks = new CopyOnWriteArrayList<>();

    public void register(Callback callback) {
        callbacks.add(callback);
    }

    public void firePreRequest(ChatCompletionRequest request) {
        for (Callback callback : callbacks) {
            try {
                callback.onPreRequest(request);
            } catch (Exception e) {
                log.warn("Callback onPreRequest failed: {}", e.getMessage(), e);
            }
        }
    }

    public void firePostRequest(ChatCompletionRequest request, ChatCompletionResponse response) {
        for (Callback callback : callbacks) {
            try {
                callback.onSuccess(request, response);
            } catch (Exception e) {
                log.warn("Callback onSuccess failed: {}", e.getMessage(), e);
            }
        }
    }

    public void fireError(ChatCompletionRequest request, Throwable error) {
        for (Callback callback : callbacks) {
            try {
                callback.onError(request, error);
            } catch (Exception e) {
                log.warn("Callback onError failed: {}", e.getMessage(), e);
            }
        }
    }
}
