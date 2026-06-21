# Real-image benchmarking

## Preconditions

Use an approved image dataset following `performance/datasets/README.md`, a manufacturer account for enrollment, and a verifier/manufacturer account for verification. The default dataset directory is empty. This is deliberate: benchmark scripts never create fake product vectors or direct database records.

## Seed real enrollments

```powershell
$env:BENCHMARK_USERNAME='manufacturer@example.com'
$env:BENCHMARK_PASSWORD='replace-with-a-real-password'
python performance/seed_real_images.py --dataset performance/datasets/sample
```

## Run the mixed, end-to-end benchmark

```powershell
python -m locust -f performance/locustfile_supplyprint_real_flow.py --headless `
  --host http://localhost:10000 -u 30 -r 5 -t 5m `
  --csv performance/results/real_flow
```

Traffic mix: 75% image verification, 10% image enrollment (each actual enrollment file is consumed at most once), 10% evidence reads, and 5% dashboard reads. Locust reports p50/p95/p99, throughput, and error rate; the final summary reports enrolled products and persisted `verification_events` through the dashboard API.

## Interpretation

This benchmark measures request/inference/database infrastructure latency. It does **not** establish anti-counterfeit accuracy. If the dataset is sample, small, synthetic, or uncontrolled, label results **sample/prototype dataset only**. Use `scripts/evaluate_model.py` with a controlled labeled dataset for FAR, FRR, EER, ROC-AUC, and threshold analysis.
