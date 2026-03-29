package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateUserRequest(
        String userId,
        String userEmail,
        String userRole,
        UUID organizationId,
        BigDecimal maxBudget,
        Long tpmLimit,
        Long rpmLimit
) {
}
