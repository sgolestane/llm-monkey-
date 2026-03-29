package ai.llmmonkey.api.exception;

import ai.llmmonkey.provider.ProviderType;

public final class ProviderException extends LlmMonkeyException {

    private final ProviderType providerType;

    public ProviderException(String message, int statusCode, ProviderType providerType) {
        super(message, statusCode, "provider_error", "provider_error");
        this.providerType = providerType;
    }

    public ProviderType getProviderType() { return providerType; }
}
