package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;

public record CreateTagBudgetRequest(
        String tag,
        BigDecimal maxBudget,
        BigDecimal softBudget,
        Long tpmLimit,
        Long rpmLimit,
        String budgetDuration
) {
}
