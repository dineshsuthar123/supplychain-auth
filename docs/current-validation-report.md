# Current validation report

## Classification

**Production-style prototype.**

## Current evidence

- Dataset validation ran on 2026-06-20. It found 0 products, 0 errors, and correctly classified the repository dataset as **insufficient**. See `reports/dataset-validation/latest.json`.
- Model evaluation ran on the same empty dataset and correctly refused a field-accuracy result: 0 reference, genuine, and mismatch images. See `reports/model-evaluation/latest.json`.
- The real-image smoke test correctly refuses to run without one reference, one held-out genuine, and one mismatch JPEG/PNG capture. No smoke result has been fabricated.
- Local PostgreSQL dashboard read benchmark: 442 requests, 0% failures, p50 7 ms, p95 11 ms, p99 14 ms.
- Docker service starts with PostgreSQL and ONNX loaded.
- Protected API checks reject anonymous dashboard/evidence/enrollment access; an authenticated tenant user reaches only its tenant dashboard.

## Metrics availability

The application instruments decode, ONNX inference, embedding normalization, verification database lookup, audit write, verification total, enrollment total, dashboard query, verification result count, and blockchain outbox pending count. `scripts/check_metrics.py` checks the actual Prometheus-exported names after a real flow has executed. It has not produced a passing report yet because no real-image request has run and no local admin metrics token was provisioned.

## Security and tenant limitation

Core capture, evidence, and dashboard queries are JWT-protected and tenant-scoped. The smoke test uses one newly registered manufacturer because the current prototype has no tenant-membership/invitation workflow for creating a separate verifier within the same tenant. This validates the correct-tenant API flow, but not a multi-user tenant collaboration model.

## Evidence still required

1. Run `scripts/validate_dataset.py` over a controlled real dataset.
2. Run `scripts/smoke_real_image_flow.py` with one genuine/mismatch product set.
3. Run `scripts/evaluate_model.py` and inspect FAR/FRR/EER/ROC-AUC.
4. Run real-flow Locust and metrics validation after the smoke test.
5. Add a tenant membership/invitation test before representing multi-user tenant operations as complete.

No anti-counterfeit accuracy, field reliability, or production-ready classification is justified until those steps use a representative real labeled capture dataset.
