CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alias           VARCHAR(255) NOT NULL UNIQUE,
    budget_id       UUID REFERENCES budgets(id),
    metadata        JSONB DEFAULT '{}',
    models          TEXT[] DEFAULT '{}',
    spend           DECIMAL(20,10) DEFAULT 0.0,
    model_spend     JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT now(),
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      VARCHAR(255) NOT NULL
);
