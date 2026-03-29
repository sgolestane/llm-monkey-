package ai.llmmonkey.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PiiMaskingGuardrail implements Guardrail {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b(\\+?1?[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b");

    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");

    @Override
    public GuardrailResult check(String content) {
        List<String> violations = new ArrayList<>();

        if (EMAIL_PATTERN.matcher(content).find()) {
            violations.add("PII detected: email address");
        }
        if (PHONE_PATTERN.matcher(content).find()) {
            violations.add("PII detected: phone number");
        }
        if (SSN_PATTERN.matcher(content).find()) {
            violations.add("PII detected: SSN");
        }
        if (CREDIT_CARD_PATTERN.matcher(content).find()) {
            violations.add("PII detected: credit card number");
        }

        return violations.isEmpty() ? GuardrailResult.pass() : GuardrailResult.fail(violations);
    }

    public String mask(String content) {
        String masked = EMAIL_PATTERN.matcher(content).replaceAll("[EMAIL]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE]");
        masked = SSN_PATTERN.matcher(masked).replaceAll("[SSN]");
        masked = CREDIT_CARD_PATTERN.matcher(masked).replaceAll("[CREDIT_CARD]");
        return masked;
    }

    @Override
    public String name() {
        return "PiiMaskingGuardrail";
    }
}
