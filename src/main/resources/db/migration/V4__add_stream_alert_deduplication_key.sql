ALTER TABLE alerts ADD COLUMN deduplication_key VARCHAR(512);
CREATE UNIQUE INDEX idx_alert_deduplication_key ON alerts (deduplication_key);
