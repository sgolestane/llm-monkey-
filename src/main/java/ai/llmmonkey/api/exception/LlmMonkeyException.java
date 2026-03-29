package ai.llmmonkey.api.exception;

public sealed class LlmMonkeyException extends RuntimeException
        permits ProviderException, ModelNotFoundException, RateLimitException,
                BudgetExceededException, AuthenticationException, AuthorizationException,
                GuardrailViolationException, ConfigurationException {

    private final int statusCode;
    private final String errorType;
    private final String errorCode;

    public LlmMonkeyException(String message, int statusCode, String errorType, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorCode = errorCode;
    }

    public int getStatusCode() { return statusCode; }
    public String getErrorType() { return errorType; }
    public String getErrorCode() { return errorCode; }
}
