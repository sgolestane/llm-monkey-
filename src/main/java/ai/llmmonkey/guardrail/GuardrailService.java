package ai.llmmonkey.guardrail;

import ai.llmmonkey.api.dto.ChatMessage;
import ai.llmmonkey.api.exception.GuardrailViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class GuardrailService {

    private final List<Guardrail> guardrails;

    public GuardrailService(List<Guardrail> guardrails) {
        this.guardrails = guardrails;
    }

    public Mono<Void> checkInput(List<ChatMessage> messages) {
        return Mono.fromRunnable(() -> {
            List<String> allViolations = new ArrayList<>();
            for (ChatMessage message : messages) {
                String content = message.contentAsString();
                if (content == null || content.isEmpty()) {
                    continue;
                }
                for (Guardrail guardrail : guardrails) {
                    GuardrailResult result = guardrail.check(content);
                    if (!result.passed()) {
                        allViolations.addAll(result.violations());
                    }
                }
            }
            if (!allViolations.isEmpty()) {
                throw new GuardrailViolationException(allViolations);
            }
        });
    }

    public Mono<Void> checkOutput(String content) {
        return Mono.fromRunnable(() -> {
            List<String> allViolations = new ArrayList<>();
            for (Guardrail guardrail : guardrails) {
                GuardrailResult result = guardrail.check(content);
                if (!result.passed()) {
                    allViolations.addAll(result.violations());
                }
            }
            if (!allViolations.isEmpty()) {
                throw new GuardrailViolationException(allViolations);
            }
        });
    }
}
