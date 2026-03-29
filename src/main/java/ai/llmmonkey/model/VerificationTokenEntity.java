package ai.llmmonkey.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_tokens")
public class VerificationTokenEntity {

    @Id
    @Column(name = "token")
    private String token;

    @Column(name = "key_name")
    private String keyName;

    @Column(name = "key_alias")
    private String keyAlias;

    @Column(name = "spend", precision = 30, scale = 10)
    private BigDecimal spend;

    @Column(name = "expires", columnDefinition = "TIMESTAMPTZ")
    private Instant expires;

    @Column(name = "models", columnDefinition = "text[]")
    private String models;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "team_id", columnDefinition = "uuid")
    private UUID teamId;

    @Column(name = "organization_id", columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "budget_id", columnDefinition = "uuid")
    private UUID budgetId;

    @Column(name = "max_parallel_requests")
    private Integer maxParallelRequests;

    @Column(name = "tpm_limit")
    private Long tpmLimit;

    @Column(name = "rpm_limit")
    private Long rpmLimit;

    @Column(name = "max_budget", precision = 30, scale = 10)
    private BigDecimal maxBudget;

    @Column(name = "budget_duration")
    private String budgetDuration;

    @Column(name = "budget_reset_at", columnDefinition = "TIMESTAMPTZ")
    private Instant budgetResetAt;

    @Column(name = "model_spend", columnDefinition = "jsonb")
    private String modelSpend;

    @Column(name = "model_max_budget", columnDefinition = "jsonb")
    private String modelMaxBudget;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "allowed_routes", columnDefinition = "text[]")
    private String allowedRoutes;

    @Column(name = "blocked")
    private boolean blocked;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public BigDecimal getSpend() {
        return spend;
    }

    public void setSpend(BigDecimal spend) {
        this.spend = spend;
    }

    public Instant getExpires() {
        return expires;
    }

    public void setExpires(Instant expires) {
        this.expires = expires;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(UUID budgetId) {
        this.budgetId = budgetId;
    }

    public Integer getMaxParallelRequests() {
        return maxParallelRequests;
    }

    public void setMaxParallelRequests(Integer maxParallelRequests) {
        this.maxParallelRequests = maxParallelRequests;
    }

    public Long getTpmLimit() {
        return tpmLimit;
    }

    public void setTpmLimit(Long tpmLimit) {
        this.tpmLimit = tpmLimit;
    }

    public Long getRpmLimit() {
        return rpmLimit;
    }

    public void setRpmLimit(Long rpmLimit) {
        this.rpmLimit = rpmLimit;
    }

    public BigDecimal getMaxBudget() {
        return maxBudget;
    }

    public void setMaxBudget(BigDecimal maxBudget) {
        this.maxBudget = maxBudget;
    }

    public String getBudgetDuration() {
        return budgetDuration;
    }

    public void setBudgetDuration(String budgetDuration) {
        this.budgetDuration = budgetDuration;
    }

    public Instant getBudgetResetAt() {
        return budgetResetAt;
    }

    public void setBudgetResetAt(Instant budgetResetAt) {
        this.budgetResetAt = budgetResetAt;
    }

    public String getModelSpend() {
        return modelSpend;
    }

    public void setModelSpend(String modelSpend) {
        this.modelSpend = modelSpend;
    }

    public String getModelMaxBudget() {
        return modelMaxBudget;
    }

    public void setModelMaxBudget(String modelMaxBudget) {
        this.modelMaxBudget = modelMaxBudget;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getAllowedRoutes() {
        return allowedRoutes;
    }

    public void setAllowedRoutes(String allowedRoutes) {
        this.allowedRoutes = allowedRoutes;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
