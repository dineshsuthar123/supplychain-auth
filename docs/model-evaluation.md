# Model evaluation

`scripts/evaluate_model.py` evaluates the ONNX model with the same grayscale, 256x256, [0,1] preprocessing used by the backend. It compares each product's first genuine image to remaining genuine and explicitly labeled mismatch captures.

```powershell
python scripts/evaluate_model.py --dataset datasets/eval/products
```

It writes JSON and Markdown reports under `reports/model-evaluation/`. The report includes FAR, FRR, approximate EER, ROC-AUC, a threshold recommendation, and genuine/mismatch similarity summaries. If there are fewer than two products, four genuine captures, or two mismatch images, it emits an insufficient-data warning instead of scores.

Results are reproducible for the same model and files. They are not a field-accuracy claim unless the capture protocol, provenance, labels, data size, and held-out evaluation design are documented.
