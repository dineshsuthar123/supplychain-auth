ALTER TABLE products ADD COLUMN IF NOT EXISTS metadata_uri VARCHAR(2048);
ALTER TABLE products ADD COLUMN IF NOT EXISTS registered_at TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS nft_token_id VARCHAR(255);

UPDATE products
SET registered_at = created_at
WHERE registered_at IS NULL;

ALTER TABLE products ALTER COLUMN registered_at SET NOT NULL;
