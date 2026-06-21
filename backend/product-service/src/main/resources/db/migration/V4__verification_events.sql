CREATE TABLE verification_events (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id           VARCHAR(255) NOT NULL,
    verified             BOOLEAN NOT NULL,
    similarity           DOUBLE PRECISION NOT NULL,
    blockchain_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_events_created_at ON verification_events(created_at DESC);
CREATE INDEX idx_verification_events_product_created ON verification_events(product_id, created_at DESC);
