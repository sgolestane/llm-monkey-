package ai.llmmonkey.auth;

import ai.llmmonkey.admin.dto.GenerateKeyRequest;
import ai.llmmonkey.admin.dto.GenerateKeyResponse;
import ai.llmmonkey.admin.dto.UpdateKeyRequest;
import ai.llmmonkey.audit.AuditService;
import ai.llmmonkey.model.UserRole;
import ai.llmmonkey.repository.BudgetRepository;
import ai.llmmonkey.repository.VerificationTokenRepository;
import ai.llmmonkey.model.VerificationTokenEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VirtualKeyService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final BudgetRepository budgetRepository;
    private final KeyGenerator keyGenerator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public VirtualKeyService(VerificationTokenRepository verificationTokenRepository,
                             BudgetRepository budgetRepository,
                             KeyGenerator keyGenerator,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.budgetRepository = budgetRepository;
        this.keyGenerator = keyGenerator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public GenerateKeyResponse generateKey(GenerateKeyRequest request, String createdBy) {
        String plainKey = keyGenerator.generateKey();
        String tokenHash = KeyHasher.hash(plainKey);

        VerificationTokenEntity entity = new VerificationTokenEntity();
        entity.setToken(tokenHash);
        entity.setKeyName(request.keyName());
        entity.setUserId(request.userId());
        entity.setTeamId(request.teamId());
        entity.setOrganizationId(request.organizationId());
        entity.setModels(joinList(request.models()));
        entity.setMaxBudget(request.maxBudget());
        entity.setTpmLimit(request.tpmLimit());
        entity.setRpmLimit(request.rpmLimit());
        entity.setBudgetDuration(request.budgetDuration());
        entity.setExpires(request.expires());
        entity.setMetadata(serializeMap(request.metadata()));

        verificationTokenRepository.save(entity);

        auditService.logAction("CREATE", "verification_token", tokenHash, null, entity, createdBy, tokenHash);

        return new GenerateKeyResponse(plainKey, request.keyName(), tokenHash, request.expires(), request.maxBudget());
    }

    public Optional<AuthContext> validateKey(String bearerToken) {
        String tokenHash = KeyHasher.hash(bearerToken);
        return verificationTokenRepository.findById(tokenHash)
                .map(entity -> new AuthContext(
                        tokenHash,
                        entity.getUserId(),
                        entity.getTeamId(),
                        entity.getOrganizationId(),
                        UserRole.TEAM_MEMBER,
                        entity.getMaxBudget(),
                        entity.getTpmLimit(),
                        entity.getRpmLimit(),
                        null,
                        null
                ));
    }

    public List<VerificationTokenEntity> listKeys(String userId) {
        if (userId != null && !userId.isBlank()) {
            return verificationTokenRepository.findByUserId(userId);
        }
        return verificationTokenRepository.findAll();
    }

    public void deleteKey(String tokenHash) {
        verificationTokenRepository.findById(tokenHash).ifPresent(entity -> {
            auditService.logAction("DELETE", "verification_token", tokenHash, entity, null, null, tokenHash);
            verificationTokenRepository.delete(entity);
        });
    }

    public VerificationTokenEntity updateKey(String tokenHash, UpdateKeyRequest request) {
        VerificationTokenEntity entity = verificationTokenRepository.findById(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Key not found: " + tokenHash));

        Object before = entity.toString();

        if (request.keyName() != null) entity.setKeyName(request.keyName());
        if (request.models() != null) entity.setModels(joinList(request.models()));
        if (request.maxBudget() != null) entity.setMaxBudget(request.maxBudget());
        if (request.tpmLimit() != null) entity.setTpmLimit(request.tpmLimit());
        if (request.rpmLimit() != null) entity.setRpmLimit(request.rpmLimit());
        if (request.budgetDuration() != null) entity.setBudgetDuration(request.budgetDuration());
        if (request.metadata() != null) entity.setMetadata(serializeMap(request.metadata()));

        VerificationTokenEntity updated = verificationTokenRepository.save(entity);

        auditService.logAction("UPDATE", "verification_token", tokenHash, before, updated, null, tokenHash);

        return updated;
    }

    private String joinList(List<String> list) {
        if (list == null) return null;
        return String.join(",", list);
    }

    private String serializeMap(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }
}
