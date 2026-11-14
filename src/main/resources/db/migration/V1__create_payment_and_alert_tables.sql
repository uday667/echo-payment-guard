CREATE TABLE payments (
    id UUID PRIMARY KEY,
    debtor_account VARCHAR(128) NOT NULL,
    creditor_account VARCHAR(128) NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL,
    channel VARCHAR(128),
    reference VARCHAR(255),
    initiated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(512) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    payment_id UUID UNIQUE REFERENCES payments(id)
);

CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    payment_ids VARCHAR(512) NOT NULL,
    idempotency_key VARCHAR(512) UNIQUE REFERENCES idempotency_keys(idempotency_key),
    debtor_account VARCHAR(128) NOT NULL,
    creditor_account VARCHAR(128) NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_alerts_status_created_at
    ON alerts (status, created_at DESC);
CREATE INDEX idx_alerts_type_status_created_at
    ON alerts (type, status, created_at DESC);
