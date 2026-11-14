CREATE TABLE alert_audit (
    id UUID PRIMARY KEY,
    alert_id UUID NOT NULL REFERENCES alerts(id),
    from_status VARCHAR(16) NOT NULL,
    to_status VARCHAR(16) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    note VARCHAR(1000),
    audit_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_alert_audit_alert_id_at
    ON alert_audit (alert_id, audit_at);
