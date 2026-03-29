package ai.llmmonkey.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tag_budgets")
public class TagBudgetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tag", nullable = false, unique = true)
    private String tag;

    @Column(name = "max_budget", precision = 20, scale = 10)
    private BigDecimal maxBudget;

    @Column(name = "soft_budget", precision = 20, scale = 10)
    private BigDecimal softBudget;

    @Column(name = "current_spend", precision = 20, scale = 10)
    private BigDecimal currentSpend;

    @Column(name = "tpm_limit")
    private Long tpmLimit;

    @Column(name = "rpm_limit")
    private Long rpmLimit;

    @Column(name = "budget_duration")
    private String budgetDuration;

    @Column(name = "budget_reset_at", columnDefinition = "TIMESTAMPTZ")
    private Instant budgetResetAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (currentSpend == null) currentSpend = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public BigDecimal getMaxBudget() { return maxBudget; }
    public void setMaxBudget(BigDecimal maxBudget) { this.maxBudget = maxBudget; }
    public BigDecimal getSoftBudget() { return softBudget; }
    public void setSoftBudget(BigDecimal softBudget) { this.softBudget = softBudget; }
    public BigDecimal getCurrentSpend() { return currentSpend; }
    public void setCurrentSpend(BigDecimal currentSpend) { this.currentSpend = currentSpend; }
    public Long getTpmLimit() { return tpmLimit; }
    public void setTpmLimit(Long tpmLimit) { this.tpmLimit = tpmLimit; }
    public Long getRpmLimit() { return rpmLimit; }
    public void setRpmLimit(Long rpmLimit) { this.rpmLimit = rpmLimit; }
    public String getBudgetDuration() { return budgetDuration; }
    public void setBudgetDuration(String budgetDuration) { this.budgetDuration = budgetDuration; }
    public Instant getBudgetResetAt() { return budgetResetAt; }
    public void setBudgetResetAt(Instant budgetResetAt) { this.budgetResetAt = budgetResetAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
