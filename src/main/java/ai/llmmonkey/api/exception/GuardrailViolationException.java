package ai.llmmonkey.api.exception;

import java.util.List;

public final class GuardrailViolationException extends LlmMonkeyException {

    private final List<String> violations;

    public GuardrailViolationException(List<String> violations) {
        super("Content policy violation: " + String.join(", ", violations), 400,
                "content_policy_error", "guardrail_violation");
        this.violations = violations;
    }

    public List<String> getViolations() { return violations; }
}
