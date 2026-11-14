CREATE TABLE processed_payment_events (
    event_id VARCHAR(256) PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    state VARCHAR(16) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processed_payment_events_processed_at
    ON processed_payment_events (processed_at);
