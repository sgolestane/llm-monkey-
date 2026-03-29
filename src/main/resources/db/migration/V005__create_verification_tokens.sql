CREATE TABLE verification_tokens (
    token                 VARCHAR(255) PRIMARY KEY,
    key_name              VARCHAR(255),
    key_alias             VARCHAR(255),
    spend                 DECIMAL(20,10) DEFAULT 0.0,
    expires               TIMESTAMPTZ,
    models                TEXT[] DEFAULT '{}',
    user_id               VARCHAR(255) REFERENCES llm_users(id),
    team_id               UUID REFERENCES teams(id),
    organization_id       UUID REFERENCES organizations(id),
    budget_id             UUID REFERENCES budgets(id),
    max_parallel_requests INT,
    tpm_limit             BIGINT,
    rpm_limit             BIGINT,
    max_budget            DECIMAL(20,10),
    budget_duration       VARCHAR(50),
    budget_reset_at       TIMESTAMPTZ,
    model_spend           JSONB DEFAULT '{}',
    model_max_budget      JSONB DEFAULT '{}',
    metadata              JSONB DEFAULT '{}',
    allowed_routes        TEXT[] DEFAULT '{}',
    blocked               BOOLEAN DEFAULT false,
    created_at            TIMESTAMPTZ DEFAULT now(),
    created_by            VARCHAR(255),
    updated_at            TIMESTAMPTZ DEFAULT now(),
    updated_by            VARCHAR(255)
);

CREATE INDEX idx_tokens_user ON verification_tokens(user_id);
CREATE INDEX idx_tokens_team ON verification_tokens(team_id);
CREATE INDEX idx_tokens_org ON verification_tokens(organization_id);
