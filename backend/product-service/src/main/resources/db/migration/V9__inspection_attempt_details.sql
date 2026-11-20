ALTER TABLE verification_events
    ADD COLUMN decision VARCHAR(24),
    ADD COLUMN warning TEXT,
    ADD COLUMN transaction_hash VARCHAR(66),
    ADD COLUMN block_number BIGINT;

CREATE INDEX idx_verification_events_tenant_product_attempt
    ON verification_events(tenant_id, product_id, created_at DESC, id DESC);
