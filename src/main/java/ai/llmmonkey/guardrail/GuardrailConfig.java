package ai.llmmonkey.guardrail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GuardrailConfig {

    @Bean
    public PiiMaskingGuardrail piiMaskingGuardrail() {
        return new PiiMaskingGuardrail();
    }
}
