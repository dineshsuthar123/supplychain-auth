-- V3: SupplyPrint – physical-fingerprint anti-counterfeit schema
-- Requires pgvector extension (pgvector/pgvector:pg16 Docker image ships it)
CREATE EXTENSION IF NOT EXISTS vector;

-- ── Core fingerprint store ──────────────────────────────────────────────────
CREATE TABLE product_fingerprints (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id       VARCHAR(255) NOT NULL UNIQUE,  -- manufacturer code + batch
    embedding        vector(128),                   -- 128-dim float32 feature vector
    feature_hash     VARCHAR(64) NOT NULL,           -- SHA-256(productId + embedding)
    metadata         TEXT,                           -- arbitrary JSON blob
    block_number     BIGINT,                         -- populated after on-chain mint
    transaction_hash VARCHAR(66),                    -- 0x-prefixed Ethereum tx hash
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fingerprints_product_id ON product_fingerprints(product_id);
-- pgvector cosine-distance IVFFlat index (lists=50 fits small datasets; tune for prod)
CREATE INDEX idx_fingerprints_embedding ON product_fingerprints
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);

-- ── Transactional outbox for exactly-once blockchain writes ─────────────────
CREATE TABLE blockchain_outbox (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   VARCHAR(255) NOT NULL,
    feature_hash VARCHAR(64) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    attempts     INT         NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_outbox_status ON blockchain_outbox(status);
