# Production roadmap

## Before any accuracy claim

Collect a representative, labeled, held-out physical-capture dataset. Document product classes, operators, devices, lighting, orientation, capture distance, labeling policy, and mismatch construction. Run the evaluator and review FAR/FRR/EER rather than choosing a threshold from intuition.

## Before multi-tenant launch

Add tenant-admin membership workflows, invitation acceptance, account recovery, immutable audit administration, and optionally PostgreSQL row-level security. Load-test cross-tenant denial cases.

## Before ledger launch

Deploy contracts through a controlled release pipeline; place keys in a secret manager; use a managed RPC provider; monitor outbox age/failure counts; define retry, reconciliation, and incident procedures.

## Before scale claims

Benchmark real image ingestion with production-like captures and data volume. Publish hardware, concurrency, database size, cache policy, p50/p95/p99, error rate, and sustained duration. Do not extrapolate local dashboard-read latency into verification capacity.
