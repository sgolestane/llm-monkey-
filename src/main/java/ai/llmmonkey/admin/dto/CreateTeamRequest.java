package ai.llmmonkey.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateTeamRequest(
        String teamAlias,
        UUID organizationId,
        List<String> models,
        BigDecimal maxBudget,
        Long tpmLimit,
        Long rpmLimit,
        Map<String, Object> metadata
) {
}
