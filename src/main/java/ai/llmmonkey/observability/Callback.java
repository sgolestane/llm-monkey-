package ai.llmmonkey.observability;

import ai.llmmonkey.api.dto.ChatCompletionRequest;
import ai.llmmonkey.api.dto.ChatCompletionResponse;

public interface Callback {

    default void onPreRequest(ChatCompletionRequest request) {
    }

    default void onSuccess(ChatCompletionRequest request, ChatCompletionResponse response) {
    }

    default void onError(ChatCompletionRequest request, Throwable error) {
    }
}
