package ai.llmmonkey.budget;

import ai.llmmonkey.api.exception.BudgetExceededException;
import ai.llmmonkey.repository.TagBudgetRepository;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Component
public class TagBudgetEnforcer {

    private final TagBudgetRepository tagBudgetRepository;

    public TagBudgetEnforcer(TagBudgetRepository tagBudgetRepository) {
        this.tagBudgetRepository = tagBudgetRepository;
    }

    public Mono<Void> checkTagBudgets(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> tagBudgetRepository.findByTagIn(tags))
                .flatMap(tagBudgets -> {
                    for (var tagBudget : tagBudgets) {
                        if (tagBudget.getMaxBudget() != null) {
                            BigDecimal currentSpend = tagBudget.getCurrentSpend() != null
                                    ? tagBudget.getCurrentSpend() : BigDecimal.ZERO;
                            if (currentSpend.compareTo(tagBudget.getMaxBudget()) >= 0) {
                                return Mono.error(new BudgetExceededException(
                                        "tag", tagBudget.getTag()));
                            }
                        }
                    }
                    return Mono.empty();
                });
    }
}
