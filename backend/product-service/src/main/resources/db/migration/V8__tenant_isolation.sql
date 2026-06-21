CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO tenants (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'legacy-default')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE product_fingerprints ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE verification_events ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE blockchain_outbox ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE users SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE product_fingerprints SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE verification_events SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE blockchain_outbox SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;

ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE product_fingerprints ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE verification_events ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE blockchain_outbox ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE product_fingerprints ADD CONSTRAINT fk_fingerprints_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE verification_events ADD CONSTRAINT fk_verification_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);
ALTER TABLE blockchain_outbox ADD CONSTRAINT fk_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id);

ALTER TABLE product_fingerprints DROP CONSTRAINT IF EXISTS product_fingerprints_product_id_key;
CREATE UNIQUE INDEX uq_fingerprints_tenant_product ON product_fingerprints(tenant_id, product_id);
CREATE INDEX idx_verification_events_tenant_created ON verification_events(tenant_id, created_at DESC);
CREATE INDEX idx_outbox_tenant_status_created ON blockchain_outbox(tenant_id, status, created_at);
