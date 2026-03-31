package ai.llmmonkey.budget;

import ai.llmmonkey.repository.BudgetRepository;
import ai.llmmonkey.model.BudgetEntity;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class BudgetResetJob {

    private final BudgetRepository budgetRepository;

    public BudgetResetJob(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "budgetResetJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void resetBudgets() {
        Instant now = Instant.now();
        List<BudgetEntity> expired = budgetRepository.findByBudgetResetAtBefore(now);

        for (BudgetEntity budget : expired) {
            Duration duration = parseDuration(budget.getBudgetDuration());
            if (duration != null) {
                budget.setBudgetResetAt(budget.getBudgetResetAt().plus(duration));
            }

            budgetRepository.save(budget);
        }
    }

    private Duration parseDuration(String budgetDuration) {
        if (budgetDuration == null || budgetDuration.isBlank()) {
            return null;
        }
        return switch (budgetDuration.toLowerCase()) {
            case "daily" -> Duration.ofDays(1);
            case "weekly" -> Duration.ofDays(7);
            case "monthly" -> Duration.ofDays(30);
            default -> {
                try {
                    yield Duration.parse(budgetDuration);
                } catch (Exception e) {
                    yield null;
                }
            }
        };
    }
}
