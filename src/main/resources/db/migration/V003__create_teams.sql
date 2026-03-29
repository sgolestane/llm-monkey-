CREATE TABLE teams (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_alias        VARCHAR(255) NOT NULL,
    organization_id   UUID REFERENCES organizations(id),
    budget_id         UUID REFERENCES budgets(id),
    metadata          JSONB DEFAULT '{}',
    models            TEXT[] DEFAULT '{}',
    spend             DECIMAL(20,10) DEFAULT 0.0,
    model_spend       JSONB DEFAULT '{}',
    max_budget        DECIMAL(20,10),
    tpm_limit         BIGINT,
    rpm_limit         BIGINT,
    blocked           BOOLEAN DEFAULT false,
    created_at        TIMESTAMPTZ DEFAULT now(),
    created_by        VARCHAR(255),
    updated_at        TIMESTAMPTZ DEFAULT now(),
    updated_by        VARCHAR(255)
);

CREATE INDEX idx_teams_org ON teams(organization_id);
