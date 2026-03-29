CREATE TABLE spend_logs (
    request_id          VARCHAR(255) PRIMARY KEY,
    call_type           VARCHAR(50) NOT NULL,
    api_key_hash        VARCHAR(255) DEFAULT '',
    spend               DECIMAL(20,10) DEFAULT 0.0,
    total_tokens        INT DEFAULT 0,
    prompt_tokens       INT DEFAULT 0,
    completion_tokens   INT DEFAULT 0,
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ NOT NULL,
    request_duration_ms INT,
    model               VARCHAR(255) DEFAULT '',
    model_group         VARCHAR(255) DEFAULT '',
    provider            VARCHAR(100) DEFAULT '',
    user_id             VARCHAR(255),
    team_id             UUID,
    organization_id     UUID,
    metadata            JSONB DEFAULT '{}',
    cache_hit           BOOLEAN DEFAULT false,
    status              VARCHAR(50),
    created_at          TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_spend_logs_start_time ON spend_logs(start_time);
CREATE INDEX idx_spend_logs_team ON spend_logs(team_id);
CREATE INDEX idx_spend_logs_user ON spend_logs(user_id);
CREATE INDEX idx_spend_logs_api_key ON spend_logs(api_key_hash);
