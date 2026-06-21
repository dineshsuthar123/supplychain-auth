# Real dataset collection

SupplyPrint needs real, controlled physical-capture evidence before any accuracy claim.

## Prototype target

Collect at least five physical products. For each product, capture at least ten enrollment/reference images, ten held-out genuine verification images, and ten mismatch images. Record device, operator, distance, angle, lighting, focus/blur, packaging state, and capture date. Deliberately vary lighting, angle, distance, blur, and camera conditions across the held-out genuine captures.

```
datasets/eval/products/
  product_001/
    genuine/
      reference/ # minimum 10; used only for enrollment/reference comparison
      verify/    # minimum 10; held-out genuine verification captures
    mismatch/    # minimum 10 non-genuine/similar-object captures
```

For backward compatibility, a flat `genuine/` directory is accepted by the tooling, but it must contain at least 20 images. The first image becomes the reference and the remainder are treated as verification images, so the nested layout is strongly preferred for a defensible evaluation.

For API benchmarking, copy one reference image per product to `performance/datasets/sample/enroll/` and use images named `product_001__view.jpg` in `verify/genuine/` and `verify/mismatch/`.

Run `python scripts/validate_dataset.py` before smoke tests or evaluation. A five-product/10-reference/10-genuine/10-mismatch dataset is prototype-scale only; it is not enough for field-grade claims.
