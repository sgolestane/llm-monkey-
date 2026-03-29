package ai.llmmonkey.guardrail;

import java.util.ArrayList;
import java.util.List;

public class KeywordGuardrail implements Guardrail {

    private final List<String> blockedKeywords;

    public KeywordGuardrail(List<String> blockedKeywords) {
        this.blockedKeywords = blockedKeywords;
    }

    @Override
    public GuardrailResult check(String content) {
        String lowerContent = content.toLowerCase();
        List<String> violations = new ArrayList<>();
        for (String keyword : blockedKeywords) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                violations.add("Content contains blocked keyword: " + keyword);
            }
        }
        return violations.isEmpty() ? GuardrailResult.pass() : GuardrailResult.fail(violations);
    }

    @Override
    public String name() {
        return "KeywordGuardrail";
    }
}
