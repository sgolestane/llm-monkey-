package ai.llmmonkey.budget;

import ai.llmmonkey.api.exception.BudgetExceededException;
import ai.llmmonkey.auth.AuthContext;
import ai.llmmonkey.repository.VerificationTokenRepository;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Component
public class BudgetEnforcer {

    private final VerificationTokenRepository verificationTokenRepository;

    public BudgetEnforcer(VerificationTokenRepository verificationTokenRepository) {
        this.verificationTokenRepository = verificationTokenRepository;
    }

    public Mono<Void> checkBudget(AuthContext ctx) {
        if (ctx.maxBudget() == null) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> verificationTokenRepository.findById(ctx.apiKeyHash()))
                .flatMap(optionalToken -> {
                    if (optionalToken.isEmpty()) {
                        return Mono.empty();
                    }
                    var token = optionalToken.get();
                    BigDecimal currentSpend = token.getSpend() != null ? token.getSpend() : BigDecimal.ZERO;
                    if (currentSpend.compareTo(ctx.maxBudget()) >= 0) {
                        return Mono.error(new BudgetExceededException(
                                "api_key", ctx.apiKeyHash()));
                    }
                    return Mono.empty();
                });
    }
}
