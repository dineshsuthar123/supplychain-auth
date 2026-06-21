# SupplyPrint

SupplyPrint is a physical-product identity and provenance prototype. It accepts a product capture image, derives a 128-dimensional visual fingerprint through server-side ONNX inference, stores that fingerprint in PostgreSQL/pgvector, and records a durable verification/audit trail. Ledger delivery is handled asynchronously through a transactional outbox.

## What it does today

```
JPEG/PNG capture -> ONNX embedding -> PostgreSQL pgvector -> feature hash -> ledger outbox
fresh capture    -> ONNX embedding -> tenant-scoped cosine comparison -> verification audit event
```

The React SPA offers enrollment, verification, evidence lookup, and a database-backed operations dashboard. The Spring Boot service enforces JWT access roles and tenant boundaries for the core fingerprint API.

## Honest capability statement

**Implemented:** real server-side image ingestion; ONNX inference; PostgreSQL/pgvector persistence; transactional outbox; tenant-scoped core queries; JWT roles; refresh-token rotation; image magic-byte validation; audit events; Micrometer metrics; and database-backed Locust tooling.

**Prototype/experimental:** anti-counterfeit accuracy. The repository does not include a representative, controlled real product-capture dataset or a validated held-out evaluation. Do not make field-accuracy or production anti-counterfeit claims until `scripts/evaluate_model.py` reports defensible FAR/FRR/EER/ROC-AUC results on such data.

**Local-only by default:** blockchain delivery is disabled in Docker Compose. Production requires a deployed contract, managed RPC, secret management, and reconciliation controls.

## Architecture

| Layer | Implementation |
|---|---|
| Web | React 18 SPA |
| API | Spring Boot 3.2 product-service |
| Identity | JWT access token, rotating HTTP-only refresh token, roles |
| AI | ONNX Runtime, grayscale 256x256 input, 128-D L2-normalized embedding |
| Database | PostgreSQL 15 + pgvector, Flyway migrations |
| Isolation | Tenant ID in user, fingerprint, outbox, and verification-event records |
| Provenance | Transactional blockchain outbox + Web3j integration |
| Operations | Spring Actuator, Prometheus, Grafana, Micrometer timers |

## Roles

| Role | Allowed core actions |
|---|---|
| `MANUFACTURER` | Enroll product captures; verify; read evidence/dashboard |
| `VERIFIER` | Verify captures; read evidence/dashboard |
| `AUDITOR` | Read evidence/dashboard |
| `ADMIN` | All core actions and metrics access |

## Local run

```powershell
docker compose up -d postgresql product-service
cd frontend
npm.cmd start
```

Service health: `http://localhost:10000/actuator/health`  
PostgreSQL host port: `5433`  
Frontend: `http://localhost:3000`

The configured browser origin defaults to `http://localhost:3000`. Override it with `SUPPLYPRINT_CORS_ALLOWED_ORIGINS` for another trusted origin.

## Core API

| Route | Role | Purpose |
|---|---|---|
| `POST /auth/register` | Public | Create a tenant-owned operator account |
| `POST /auth/login` | Public | Sign in and obtain access/refresh session |
| `POST /api/enroll/image` | Manufacturer/Admin | Enroll a JPEG/PNG physical capture |
| `POST /api/verify/image` | Verifier/Manufacturer/Admin | Verify a fresh physical capture |
| `GET /api/verify/{productId}/log` | Auditor/Verifier/Manufacturer/Admin | Read tenant-scoped evidence |
| `GET /api/dashboard` | Auditor/Verifier/Manufacturer/Admin | Read tenant-scoped operational telemetry |

## Testing and benchmarks

Run the real-image seed path only with approved captures:

```powershell
$env:BENCHMARK_USERNAME='manufacturer@example.com'
$env:BENCHMARK_PASSWORD='your-password'
python performance/seed_real_images.py --dataset performance/datasets/sample
```

Run the mixed real-flow benchmark:

```powershell
python -m locust -f performance/locustfile_supplyprint_real_flow.py --headless `
  --host http://localhost:10000 -u 30 -r 5 -t 5m `
  --csv performance/results/real_flow
```

The default dataset folder is intentionally empty. The runner does not create fake products or vectors. See [benchmarking](docs/benchmarking.md) and [dataset instructions](performance/datasets/README.md).

## Current measured infrastructure result

Local PostgreSQL aggregate-read benchmark, 30 users for 45 seconds: 442 requests, 0% failures, p50 **7 ms**, p95 **11 ms**, p99 **14 ms**. This measures the tenant-dashboard database read path only; it is not an image-verification throughput claim and does not validate detection accuracy.

## Real-world validation status

Dataset status: **insufficient**. No real labeled product-capture images are committed to this repository. The project includes a collection protocol, validator, API-only smoke test, evaluator, and real-flow Locust runner, but their reports remain deliberately cautious until real captures are supplied.

```powershell
python scripts/validate_dataset.py
python scripts/smoke_real_image_flow.py --dataset datasets/eval/products
python scripts/evaluate_model.py --dataset datasets/eval/products
$env:BENCHMARK_USERNAME='manufacturer@example.com'
$env:BENCHMARK_PASSWORD='your-password'
python performance/seed_real_images.py --dataset performance/datasets/sample
python -m locust -f performance/locustfile_supplyprint_real_flow.py --headless --host http://localhost:10000 -u 1 -r 1 -t 1m
$env:METRICS_BEARER_TOKEN='admin-access-token'
python scripts/check_metrics.py
```

See [real dataset collection](docs/real-dataset-collection.md), [metrics validation](docs/metrics-validation.md), and the [current validation report](docs/current-validation-report.md). SupplyPrint remains a production-style prototype until real-dataset results are available.

## Documentation

- [Production-readiness audit](docs/production-readiness-audit.md)
- [Security model](docs/security.md)
- [Tenant isolation](docs/tenant-isolation.md)
- [Observability](docs/observability.md)
- [Database performance](docs/database-performance.md)
- [Model evaluation](docs/model-evaluation.md)
- [Benchmarking](docs/benchmarking.md)
- [Technical project summary](docs/SUPPLYPRINT_PROJECT_SUMMARY.docx)

## Next roadmap

1. Collect a controlled real capture dataset and validate model threshold/accuracy.
2. Add enterprise tenant membership/invitation and optional PostgreSQL RLS.
3. Deploy ledger credentials and asynchronous reconciliation to managed infrastructure.
4. Run soak and capacity tests that include image decode, ONNX inference, pgvector lookup, and audit writes.
