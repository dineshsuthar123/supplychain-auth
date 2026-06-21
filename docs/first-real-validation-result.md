# First real-dataset validation result

Generated: 2026-06-21 (local workstation)

## Dataset

- **Source:** [Fashion-MNIST, Zalando Research](https://github.com/zalandoresearch/fashion-mnist), downloaded from its public source files. The reproducibility manifest, source-file SHA-256 values, split/index provenance, and decoded-image hashes are in `datasets/eval/products/PUBLIC_DATASET_MANIFEST.json`.
- **Capture type:** public dataset; not own phone captures. `Images_dataset/` contained 22 heterogeneous personal captures, but not repeat captures of five product identities, so it was not relabeled, duplicated, or used to manufacture a passing test set.
- **Products/categories:** 5 - bag, sandal, sneaker, ankle boot, and T-shirt/top.
- **Images:** 100 evaluation image files: 5 reference, 10 held-out genuine, and 5 cross-category mismatch images for each category. Reference images originate from the Fashion-MNIST training split; genuine and mismatch images originate from its test split.
- **Important limitation:** Fashion-MNIST provides category labels, not SKU, package, serial-number, counterfeit, or field-capture labels. It is an integration dataset only.

## Dataset validation

Command executed:

```powershell
python scripts/validate_dataset.py --dataset datasets/eval/products
```

Result: **passed structurally** - 5 product directories, each with 5 reference, 10 genuine, and 5 mismatch readable PNGs. The validator reports `prototype-scale` and explicitly warns that the dataset is not sufficient for field-grade anti-counterfeit claims.

## API smoke test

Command executed:

```powershell
python scripts/smoke_real_image_flow.py --dataset datasets/eval/products
```

Result: **passed** after the local PostgreSQL and product-service containers were started. The flow received HTTP 202 for enrollment, HTTP 200 for genuine and mismatch verification, and HTTP 200 for the tenant-scoped evidence log. It recorded two verification events in PostgreSQL. No image was inserted directly into the database; the smoke script posted every capture through `/api/enroll/image` and `/api/verify/image`.

The proxy genuine capture was rejected (similarity `0.2102`, below the application threshold), and the mismatch was also rejected (similarity `-0.0137`). This is expectedly not an accuracy claim for this dataset.

## Model evaluation

Command executed with an isolated evaluator environment pinned to NumPy 1.26.4 and ONNX Runtime 1.17.3:

```powershell
.\.venv-validation\Scripts\python.exe scripts/evaluate_model.py --dataset datasets/eval/products
```

The actual `fingerprint.onnx` model was used. No embeddings were mocked.

| Metric | Result |
|---|---:|
| Genuine pairs | 50 |
| Mismatch pairs | 25 |
| ROC AUC | 0.5824 |
| Recommended threshold | 0.9980377554893494 |
| FAR | 52% |
| FRR | 50% |
| EER | 51% |

Interpretation: this is poor separation on this public category-level set. It is not valid to tune a production threshold or infer anti-counterfeit accuracy from it. The raw evaluator result is retained in `reports/model-evaluation/latest.json`.

## Prometheus metrics

Command executed:

```powershell
python scripts/check_metrics.py
```

Result: **not confirmed**. The live service returned HTTP 403 because `/actuator/prometheus` requires an administrator bearer token and no `METRICS_BEARER_TOKEN` was provided. The control was not bypassed or weakened. Supply an authorized administrator token to repeat this check.

## Benchmark

The real-flow benchmark passed after the smoke test. It used a newly created manufacturer tenant, seeded five real public images through the live enrollment API, then ran Locust headlessly for five minutes with 30 users ramping at 5 users/second.

| Scope | Requests | Failures | Throughput | p50 | p95 | p99 |
|---|---:|---:|---:|---:|---:|---:|
| Aggregate | 27,106 | 0 | 90.54 req/s | 53 ms | 58 ms | 60 ms |
| `POST /api/verify/image` | 22,622 | 0 | 75.56 req/s | 54 ms | 58 ms | 61 ms |
| `GET /api/dashboard` | 1,505 | 0 | 5.03 req/s | 9 ms | 14 ms | 16 ms |
| `GET /api/verify/{productId}/log` | 2,979 | 0 | 9.95 req/s | 4 ms | 7 ms | 9 ms |

The raw Locust CSV artefacts are `performance/results/first_real_validation_clean_stats.csv` and `performance/results/first_real_validation_clean_failures.csv`.

The benchmark used a temporary local `SUPPLYPRINT_RATE_LIMIT_REQUESTS_PER_MINUTE=100000` override so it measured the API/database path instead of the default 120-RPM rate limiter. The compose default remains 120 RPM. An earlier five-minute run at the default limit was intentionally excluded from the capacity figures because its shared local-IP traffic was throttled (HTTP 429); it confirmed the limiter, not service capacity.

## Failures and limitations

1. Prometheus metrics were not confirmed because no authorized administrator bearer token was available; HTTP 403 is the correct protected-endpoint behavior.
2. The supplied own-capture directory was insufficient and heterogeneous; it must be replaced with controlled repeated captures of the same product/package identities for an own-data study.
3. Fashion-MNIST images are 28x28 public catalog categories, not package/SKU capture evidence, and mismatch examples are different product categories rather than counterfeits.
4. The observed EER of 51% is a negative result for this proxy set, not a performance claim.
5. The benchmark is local, single-instance, and uses a temporary rate-limit override. It is evidence of the exercised local API/database path, not a production capacity guarantee.

## Final classification

**pipeline validation completed; not anti-counterfeit proof**

This is not production-ready. A stronger evaluation requires large, labeled, held-out real-world product/package captures, documented acquisition protocol, counterfeit/confuser samples, and successful service-backed deployment evidence.
