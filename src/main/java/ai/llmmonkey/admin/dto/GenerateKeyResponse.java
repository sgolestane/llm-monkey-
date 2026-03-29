package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record GenerateKeyResponse(
        String key,
        String keyName,
        String token,
        Instant expires,
        BigDecimal maxBudget
) {
}
