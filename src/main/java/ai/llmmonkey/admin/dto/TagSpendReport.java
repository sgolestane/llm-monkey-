package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

public record TagSpendReport(
        String tag,
        BigDecimal totalSpend,
        int requestCount,
        int totalTokens,
        Map<String, BigDecimal> spendByModel,
        String period
) {
}
