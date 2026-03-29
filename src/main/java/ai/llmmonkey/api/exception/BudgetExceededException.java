package ai.llmmonkey.api.exception;

public final class BudgetExceededException extends LlmMonkeyException {
    public BudgetExceededException(String entityType, String entityId) {
        super("Budget exceeded for " + entityType + ": " + entityId, 400,
                "budget_error", "budget_exceeded");
    }
}
