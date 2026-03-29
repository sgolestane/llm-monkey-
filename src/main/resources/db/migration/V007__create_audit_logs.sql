CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp       TIMESTAMPTZ DEFAULT now(),
    changed_by      VARCHAR(255) DEFAULT '',
    changed_by_key  VARCHAR(255) DEFAULT '',
    action          VARCHAR(50) NOT NULL,
    table_name      VARCHAR(100) NOT NULL,
    object_id       VARCHAR(255) NOT NULL,
    before_value    JSONB,
    updated_values  JSONB
);

CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_logs_table ON audit_logs(table_name);
