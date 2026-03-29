package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerateKeyRequest(
        String keyName,
        String userId,
        UUID teamId,
        UUID organizationId,
        List<String> models,
        BigDecimal maxBudget,
        Long tpmLimit,
        Long rpmLimit,
        String budgetDuration,
        Instant expires,
        Map<String, Object> metadata
) {
}
