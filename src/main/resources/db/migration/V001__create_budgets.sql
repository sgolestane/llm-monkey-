CREATE TABLE budgets (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    max_budget            DECIMAL(20,10),
    soft_budget           DECIMAL(20,10),
    max_parallel_requests INT,
    tpm_limit             BIGINT,
    rpm_limit             BIGINT,
    model_max_budget      JSONB DEFAULT '{}',
    budget_duration       VARCHAR(50),
    budget_reset_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ DEFAULT now(),
    created_by            VARCHAR(255),
    updated_at            TIMESTAMPTZ DEFAULT now(),
    updated_by            VARCHAR(255)
);
