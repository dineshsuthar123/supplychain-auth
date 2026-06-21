# Observability

Prometheus can scrape Spring Actuator at `/actuator/prometheus` with an admin identity/ingress rule. Current timers:

| Metric | Meaning |
|---|---|
| `image.decode.duration` | Image decode, resize, grayscale preprocessing |
| `onnx.inference.duration` | ONNX tensor/session execution |
| `embedding.normalization.duration` | L2 normalization of model output |
| `verification.db.lookup.duration` | Tenant-scoped pgvector lookup |
| `verification.audit.write.duration` | `verification_events` persistence |
| `verification.total.duration` | Full verification-service duration |
| `enrollment.total.duration` | Full enrollment-service duration |
| `dashboard.query.duration` | Tenant dashboard aggregate/event query time |
| `verification.result.count` | Tagged `verified` or `rejected` result count |

`RequestTraceFilter` emits an `X-Trace-Id` response header and places `trace_id` in MDC. Extend the logback encoder to JSON in the deployment environment so MDC fields become structured JSON. Add tenant ID/product ID only after confirming data-retention and privacy policies.
