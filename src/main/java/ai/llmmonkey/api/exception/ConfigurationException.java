package ai.llmmonkey.api.exception;

public final class ConfigurationException extends LlmMonkeyException {
    public ConfigurationException(String message) {
        super(message, 500, "configuration_error", "misconfigured");
    }
}
