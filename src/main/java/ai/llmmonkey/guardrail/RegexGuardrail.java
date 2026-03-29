package ai.llmmonkey.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RegexGuardrail implements Guardrail {

    private final List<Pattern> patterns;

    public RegexGuardrail(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    @Override
    public GuardrailResult check(String content) {
        List<String> violations = new ArrayList<>();
        for (Pattern pattern : patterns) {
            if (pattern.matcher(content).find()) {
                violations.add("Content matches blocked pattern: " + pattern.pattern());
            }
        }
        return violations.isEmpty() ? GuardrailResult.pass() : GuardrailResult.fail(violations);
    }

    @Override
    public String name() {
        return "RegexGuardrail";
    }
}
