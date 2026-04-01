-- H2-compatible schema for testing (mirrors PostgreSQL migrations V001-V008)

CREATE TABLE budgets (
    id                    UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    max_budget            DECIMAL(20,10),
    soft_budget           DECIMAL(20,10),
    max_parallel_requests INT,
    tpm_limit             BIGINT,
    rpm_limit             BIGINT,
    model_max_budget      CLOB DEFAULT '{}',
    budget_duration       VARCHAR(50),
    budget_reset_at       TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(255),
    updated_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(255)
);

CREATE TABLE organizations (
    id              UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    alias           VARCHAR(255) NOT NULL UNIQUE,
    budget_id       UUID,
    metadata        CLOB DEFAULT '{}',
    models          VARCHAR(4000) DEFAULT '',
    spend           DECIMAL(20,10) DEFAULT 0.0,
    model_spend     CLOB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(255) NOT NULL,
    FOREIGN KEY (budget_id) REFERENCES budgets(id)
);

CREATE TABLE teams (
    id                UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    team_alias        VARCHAR(255) NOT NULL,
    organization_id   UUID,
    budget_id         UUID,
    metadata          CLOB DEFAULT '{}',
    models            VARCHAR(4000) DEFAULT '',
    spend             DECIMAL(20,10) DEFAULT 0.0,
    model_spend       CLOB DEFAULT '{}',
    max_budget        DECIMAL(20,10),
    tpm_limit         BIGINT,
    rpm_limit         BIGINT,
    blocked           BOOLEAN DEFAULT false,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(255),
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(255),
    FOREIGN KEY (organization_id) REFERENCES organizations(id),
    FOREIGN KEY (budget_id) REFERENCES budgets(id)
);

CREATE TABLE llm_users (
    id                VARCHAR(255) PRIMARY KEY,
    user_email        VARCHAR(255),
    user_role         VARCHAR(50) DEFAULT 'TEAM_MEMBER',
    organization_id   UUID,
    budget_id         UUID,
    spend             DECIMAL(20,10) DEFAULT 0.0,
    max_budget        DECIMAL(20,10),
    tpm_limit         BIGINT,
    rpm_limit         BIGINT,
    models            VARCHAR(4000) DEFAULT '',
    metadata          CLOB DEFAULT '{}',
    sso_user_id       VARCHAR(255),
    blocked           BOOLEAN DEFAULT false,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (organization_id) REFERENCES organizations(id),
    FOREIGN KEY (budget_id) REFERENCES budgets(id)
);

CREATE TABLE verification_tokens (
    token                 VARCHAR(255) PRIMARY KEY,
    key_name              VARCHAR(255),
    key_alias             VARCHAR(255),
    spend                 DECIMAL(20,10) DEFAULT 0.0,
    expires               TIMESTAMP WITH TIME ZONE,
    models                VARCHAR(4000) DEFAULT '',
    user_id               VARCHAR(255),
    team_id               UUID,
    organization_id       UUID,
    budget_id             UUID,
    max_parallel_requests INT,
    tpm_limit             BIGINT,
    rpm_limit             BIGINT,
    max_budget            DECIMAL(20,10),
    budget_duration       VARCHAR(50),
    budget_reset_at       TIMESTAMP WITH TIME ZONE,
    model_spend           CLOB DEFAULT '{}',
    model_max_budget      CLOB DEFAULT '{}',
    metadata              CLOB DEFAULT '{}',
    allowed_routes        VARCHAR(4000) DEFAULT '',
    blocked               BOOLEAN DEFAULT false,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(255),
    updated_at            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(255)
);

CREATE TABLE spend_logs (
    request_id          VARCHAR(255) PRIMARY KEY,
    call_type           VARCHAR(50) NOT NULL,
    api_key_hash        VARCHAR(255) DEFAULT '',
    spend               DECIMAL(20,10) DEFAULT 0.0,
    total_tokens        INT DEFAULT 0,
    prompt_tokens       INT DEFAULT 0,
    completion_tokens   INT DEFAULT 0,
    start_time          TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time            TIMESTAMP WITH TIME ZONE NOT NULL,
    request_duration_ms INT,
    model               VARCHAR(255) DEFAULT '',
    model_group         VARCHAR(255) DEFAULT '',
    provider            VARCHAR(100) DEFAULT '',
    user_id             VARCHAR(255),
    team_id             UUID,
    organization_id     UUID,
    metadata            CLOB DEFAULT '{}',
    cache_hit           BOOLEAN DEFAULT false,
    status              VARCHAR(50),
    tags                VARCHAR(4000) DEFAULT '',
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id              UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    timestamp       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    changed_by      VARCHAR(255) DEFAULT '',
    changed_by_key  VARCHAR(255) DEFAULT '',
    action          VARCHAR(50) NOT NULL,
    table_name      VARCHAR(100) NOT NULL,
    object_id       VARCHAR(255) NOT NULL,
    before_value    CLOB,
    updated_values  CLOB
);

CREATE TABLE tag_budgets (
    id                UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    tag               VARCHAR(255) NOT NULL UNIQUE,
    max_budget        DECIMAL(20,10),
    soft_budget       DECIMAL(20,10),
    current_spend     DECIMAL(20,10) DEFAULT 0.0,
    tpm_limit         BIGINT,
    rpm_limit         BIGINT,
    budget_duration   VARCHAR(50),
    budget_reset_at   TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
