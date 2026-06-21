# SupplyPrint benchmark datasets

This directory is intentionally empty. Do not commit customer captures, personally identifying images, or synthetic vectors here.

## Dataset layout

```
performance/datasets/sample/
  enroll/
    PRODUCT-001.jpg
  verify/
    genuine/
      PRODUCT-001__view-01.jpg
    mismatch/
      PRODUCT-001__different-object-01.jpg
```

`enroll/<product_id>.<jpg|jpeg|png>` is uploaded through `POST /api/enroll/image`. Verification filenames must begin with the same product ID followed by `__`; the suffix is descriptive only.

## Dataset status

The `sample` folder is a prototype dataset location, not a field-validation dataset. Any benchmark that uses it must be labelled **sample/prototype dataset only**. No anti-counterfeit accuracy claim may be made from a small, synthetic, or uncontrolled capture set.

## Capture protocol

For a defensible evaluation, collect multiple genuine captures per product across devices, lighting, orientation, distance, packaging age, and operators. Mismatch images must be visually/plausibly similar non-genuine objects. Store dataset provenance, consent, device metadata, capture conditions, and a stable product label outside the application database.

For the controlled evaluation layout, keep references and held-out genuine images separately in `datasets/eval/products/<product>/genuine/reference/` and `genuine/verify/`; copy only approved benchmark inputs into this directory.
