package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UpdateKeyRequest(
        String keyName,
        List<String> models,
        BigDecimal maxBudget,
        Long tpmLimit,
        Long rpmLimit,
        String budgetDuration,
        Map<String, Object> metadata
) {
}
