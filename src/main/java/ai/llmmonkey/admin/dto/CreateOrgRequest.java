package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CreateOrgRequest(
        String alias,
        List<String> models,
        BigDecimal maxBudget,
        Map<String, Object> metadata
) {
}
