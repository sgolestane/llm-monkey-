-- Add tags column to spend_logs for per-request tag tracking
ALTER TABLE spend_logs ADD COLUMN tags TEXT[] DEFAULT '{}';
CREATE INDEX idx_spend_logs_tags ON spend_logs USING GIN(tags);

-- Tag budgets table for per-tag budget enforcement
CREATE TABLE tag_budgets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag               VARCHAR(255) NOT NULL UNIQUE,
    max_budget        DECIMAL(20,10),
    soft_budget       DECIMAL(20,10),
    current_spend     DECIMAL(20,10) DEFAULT 0.0,
    tpm_limit         BIGINT,
    rpm_limit         BIGINT,
    budget_duration   VARCHAR(50),
    budget_reset_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_tag_budgets_tag ON tag_budgets(tag);
