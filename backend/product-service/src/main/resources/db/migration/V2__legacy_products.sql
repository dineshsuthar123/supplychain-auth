-- V2: Legacy products table (kept for backward-compat; not used by SupplyPrint core)
CREATE TABLE IF NOT EXISTS products (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    serial_number VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255),
    manufacturer  VARCHAR(255),
    status        VARCHAR(50) DEFAULT 'ACTIVE',
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);
