# Database performance

Verification receives a known `product_id`, so the primary access path is the tenant/product unique index, not global nearest-neighbor retrieval. The verification query now returns only feature hash, transaction status, block number, and cosine similarity; it does not transfer the stored vector or hydrate a second entity.

The dashboard uses one tenant-scoped aggregate query and one bounded tenant-scoped event query. Audit indexes support time/result telemetry; the outbox pending index supports dispatch ordering. Do not add HNSW until a product-ID-free nearest-neighbor feature is introduced and recall/latency is benchmarked on a representative corpus.
