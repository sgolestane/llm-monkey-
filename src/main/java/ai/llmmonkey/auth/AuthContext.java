package ai.llmmonkey.auth;

import ai.llmmonkey.model.UserRole;

import java.math.BigDecimal;
import java.util.UUID;

public record AuthContext(
        String apiKeyHash,
        String userId,
        UUID teamId,
        UUID organizationId,
        UserRole role,
        BigDecimal maxBudget,
        Long tpmLimit,
        Long rpmLimit
) {
}
