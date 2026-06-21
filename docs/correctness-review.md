# Production correctness review - 20 June 2026

## Findings fixed

1. Refresh cookies were always `Secure`, preventing local HTTP refresh. `SUPPLYPRINT_COOKIES_SECURE` now defaults true for production and Compose sets false for localhost only.
2. Public registration could self-assign manufacturer/verifier. Self-service operator roles now require explicit `SUPPLYPRINT_ALLOW_SELF_SERVICE_OPERATOR_ROLES=true`; Compose enables this local-demo convenience only.
3. Raw JSON-vector endpoints remained reachable by operational roles. They now require admin; image endpoints are the normal product path.
4. The real-flow Locust report showed only final verification count. It now records before/after/delta.
5. The evaluator calculated a threshold sweep but did not emit it. `latest.json` now includes `threshold_sweep`; a deterministic unit test covers perfect known scores.
6. `blockchain.outbox.pending.count` and dispatch duration are now registered through Micrometer.

## Live checks run

| Check | Result |
|---|---|
| Docker product-service build | Passed |
| Service health | `UP` |
| Anonymous dashboard | `403` |
| Anonymous evidence log | `403` |
| Verifier enrollment attempt | `403` |
| Verifier verification route | authorization accepted; malformed no-multipart request returned `415` |
| Authenticated verifier dashboard | `200`, tenant-scoped empty dashboard |
| Evaluation metric unit test | Passed |
| Evaluator with current dataset | Insufficient-data warning; 0 products/0 captures |

## Metrics

The service registers `image.decode.duration`, `onnx.inference.duration`, `embedding.normalization.duration`, `verification.db.lookup.duration`, `verification.audit.write.duration`, `verification.total.duration`, `enrollment.total.duration`, `dashboard.query.duration`, `verification.result.count`, `blockchain.outbox.pending.count`, and `blockchain.outbox.dispatch.duration`.

The dashboard timer was exercised by the authenticated dashboard call. Image/ONNX/verification-write timers cannot be truthfully exercised without a real JPEG/PNG product capture. `/actuator/prometheus` is intentionally admin-protected, so scrape it with an admin identity or protected Prometheus target after provisioning an admin.

## Remaining blockers

No real labeled product-capture dataset exists, so full-flow benchmark smoke testing, two-tenant product-access proof with actual records, FAR/FRR/EER values, and image-path timer verification remain blocked by data rather than code. This repository is suitable to present as a **production-style prototype**, not a production-ready anti-counterfeit system.
