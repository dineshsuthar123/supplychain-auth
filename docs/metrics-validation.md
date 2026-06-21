# Metrics validation

After a successful real-image smoke test, provision an admin JWT and run:

```powershell
$env:METRICS_BEARER_TOKEN='admin-access-token'
python scripts/check_metrics.py
```

Micrometer converts names to Prometheus snake case. Expected exposed families are `image_decode_duration_seconds`, `onnx_inference_duration_seconds`, `embedding_normalization_duration_seconds`, `verification_db_lookup_duration_seconds`, `verification_audit_write_duration_seconds`, `verification_total_duration_seconds`, `enrollment_total_duration_seconds`, `dashboard_query_duration_seconds`, `verification_result_count_total`, and `blockchain_outbox_pending_count`.

Timers are lazily visible only after their path executes. With no real capture dataset, image/inference/verification timers may not yet appear; that is an honest missing-evidence state, not a zero-latency result.
