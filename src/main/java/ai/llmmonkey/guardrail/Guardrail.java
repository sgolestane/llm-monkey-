package ai.llmmonkey.guardrail;

public interface Guardrail {

    GuardrailResult check(String content);

    String name();
}
