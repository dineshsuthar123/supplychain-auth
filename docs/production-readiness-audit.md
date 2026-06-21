# SupplyPrint production-readiness audit

_Audited: 20 June 2026_

## System as implemented

SupplyPrint is a React SPA backed by the Spring Boot `product-service`. The core product path accepts a JPEG/PNG physical capture, derives a 128-dimensional ONNX embedding server-side, stores the vector and feature hash in PostgreSQL/pgvector, and creates a transactional blockchain outbox record. A fresh capture can be verified against the known `product_id`; the result and similarity are persisted in `verification_events`.

| Area | Current implementation | Readiness finding |
|---|---|---|
| Frontend | Dashboard, enrollment image upload, verification image upload, evidence lookup, authentication | Core screens exist; all operational figures must remain API-derived. |
| Backend modules | Product service contains auth, image/embedding, fingerprint, ledger outbox, legacy products, metrics, and Swagger/Actuator | Core service is cohesive, but legacy endpoints need the same authorization/tenant controls as core endpoints. |
| Persistence | PostgreSQL 15 + pgvector; Flyway migrations for fingerprints, outbox, verification events, and schema repairs | Correct core storage shape. Product-ID verification should use the unique ID index and narrow projection; HNSW is not justified for this known-ID flow. |
| Model | ONNX model loaded in-process from `models/fingerprint.onnx`, grayscale 256x256 preprocessing, 128-D output | Runtime is real. Training script currently demonstrates synthetic texture data, so no field-accuracy claim is defensible yet. |
| Enrollment | `/api/enroll/image` validates upload, calls ONNX, persists fingerprint and outbox | Needs protected manufacturer/admin role and tenant ownership. |
| Verification | `/api/verify/image` calls ONNX, calculates pgvector cosine similarity, writes audit event | Needs protected verifier role, tenant query boundary, and real-capture benchmark/evaluation dataset. |
| Auth | BCrypt password hashing, JWT access token, refresh cookie flow | Original route policy was permissive; authentication and refresh-token persistence need hard enforcement/rotation checks. |
| Metrics | Actuator, Prometheus, Micrometer enrollment/verification/vector timers, dashboard API | Need component-level image/ONNX/DB/audit/total timers and tenant/trace structured context. |
| Performance | Database dashboard Locust baseline executed against live Postgres | Existing result proves aggregate-read infrastructure only; it does not evaluate image inference, verification writes, or detection accuracy. |
| Docker | Compose PostgreSQL on 5433, service on 10000; Debian/Jammy runtime required for ONNX glibc dependency | Local stack is healthy after correcting port and runtime-image defects. |

## Principal production gaps

1. A real, controlled product-capture dataset and model-evaluation evidence are required before claiming anti-counterfeit accuracy.
2. JWT role enforcement, CORS restriction, safe content validation, request rate limits, and audit-friendly logging need to protect the core API.
3. Every core record and lookup needs an explicit tenant boundary.
4. Full-flow benchmark runs must upload actual images through public APIs and report separate infrastructure latency versus model accuracy.
5. Blockchain is deliberately disabled in local Compose. Production requires a deployed contract, managed RPC, secret management, monitoring, and reconciliation policy.

## Measured baseline

The latest local database-only dashboard test used 30 Locust users for 45 seconds against the running PostgreSQL service. The optimized aggregate path recorded 442 requests, 0 failures, p50 7 ms, p95 11 ms, and p99 14 ms. This is not a verification-through-image or field-accuracy claim.

## Audit acceptance criteria

The repository can be considered a credible prototype when the real-flow benchmark, model evaluation report, protected tenant-scoped APIs, and component latency metrics described in the accompanying documentation all run from a clean environment without synthetic mainline data.
