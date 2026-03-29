package ai.llmmonkey.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class BudgetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "max_budget", precision = 30, scale = 10)
    private BigDecimal maxBudget;

    @Column(name = "soft_budget", precision = 30, scale = 10)
    private BigDecimal softBudget;

    @Column(name = "max_parallel_requests")
    private Integer maxParallelRequests;

    @Column(name = "tpm_limit")
    private Long tpmLimit;

    @Column(name = "rpm_limit")
    private Long rpmLimit;

    @Column(name = "model_max_budget", columnDefinition = "jsonb")
    private String modelMaxBudget;

    @Column(name = "budget_duration")
    private String budgetDuration;

    @Column(name = "budget_reset_at", columnDefinition = "TIMESTAMPTZ")
    private Instant budgetResetAt;

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getMaxBudget() {
        return maxBudget;
    }

    public void setMaxBudget(BigDecimal maxBudget) {
        this.maxBudget = maxBudget;
    }

    public BigDecimal getSoftBudget() {
        return softBudget;
    }

    public void setSoftBudget(BigDecimal softBudget) {
        this.softBudget = softBudget;
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

    public String getModelMaxBudget() {
        return modelMaxBudget;
    }

    public void setModelMaxBudget(String modelMaxBudget) {
        this.modelMaxBudget = modelMaxBudget;
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
