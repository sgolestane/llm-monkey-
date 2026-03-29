package ai.llmmonkey.budget;

import ai.llmmonkey.model.SpendLogEntity;
import ai.llmmonkey.repository.SpendLogRepository;
import ai.llmmonkey.repository.VerificationTokenRepository;
import ai.llmmonkey.repository.TeamRepository;
import ai.llmmonkey.repository.OrganizationRepository;
import ai.llmmonkey.repository.UserRepository;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class SpendTracker {

    private final SpendLogRepository spendLogRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final TeamRepository teamRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public SpendTracker(SpendLogRepository spendLogRepository,
                        VerificationTokenRepository verificationTokenRepository,
                        TeamRepository teamRepository,
                        OrganizationRepository organizationRepository,
                        UserRepository userRepository) {
        this.spendLogRepository = spendLogRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.teamRepository = teamRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Async
    public void recordSpend(String apiKeyHash, String userId, UUID teamId, UUID orgId,
                            String model, String provider, BigDecimal cost,
                            int promptTokens, int completionTokens, long durationMs) {
        SpendLogEntity log = new SpendLogEntity();
        log.setApiKeyHash(apiKeyHash);
        log.setUserId(userId);
        log.setTeamId(teamId);
        log.setOrganizationId(orgId);
        log.setModel(model);
        log.setProvider(provider);
        log.setSpend(cost);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setRequestDurationMs((int) durationMs);
        log.setCreatedAt(Instant.now());
        spendLogRepository.save(log);

        // Update spend on verification token
        verificationTokenRepository.findById(apiKeyHash).ifPresent(token -> {
            BigDecimal currentSpend = token.getSpend() != null ? token.getSpend() : BigDecimal.ZERO;
            token.setSpend(currentSpend.add(cost));
            verificationTokenRepository.save(token);
        });

        // Update spend on team
        if (teamId != null) {
            teamRepository.findById(teamId).ifPresent(team -> {
                BigDecimal currentSpend = team.getSpend() != null ? team.getSpend() : BigDecimal.ZERO;
                team.setSpend(currentSpend.add(cost));
                teamRepository.save(team);
            });
        }

        // Update spend on organization
        if (orgId != null) {
            organizationRepository.findById(orgId).ifPresent(org -> {
                BigDecimal currentSpend = org.getSpend() != null ? org.getSpend() : BigDecimal.ZERO;
                org.setSpend(currentSpend.add(cost));
                organizationRepository.save(org);
            });
        }

        // Update spend on user
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                BigDecimal currentSpend = user.getSpend() != null ? user.getSpend() : BigDecimal.ZERO;
                user.setSpend(currentSpend.add(cost));
                userRepository.save(user);
            });
        }
    }
}
