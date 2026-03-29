CREATE TABLE llm_users (
    id                VARCHAR(255) PRIMARY KEY,
    user_email        VARCHAR(255),
    user_role         VARCHAR(50) DEFAULT 'team_member',
    organization_id   UUID REFERENCES organizations(id),
    budget_id         UUID REFERENCES budgets(id),
    spend             DECIMAL(20,10) DEFAULT 0.0,
    max_budget        DECIMAL(20,10),
    tpm_limit         BIGINT,
    rpm_limit         BIGINT,
    models            TEXT[] DEFAULT '{}',
    metadata          JSONB DEFAULT '{}',
    sso_user_id       VARCHAR(255),
    blocked           BOOLEAN DEFAULT false,
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_users_email ON llm_users(user_email);
CREATE INDEX idx_users_org ON llm_users(organization_id);
