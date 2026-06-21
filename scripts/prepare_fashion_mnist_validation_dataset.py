#!/usr/bin/env python3
"""Prepare a reproducible Fashion-MNIST subset for pipeline validation only.

The source is a public, labeled catalog-image benchmark.  Its labels are
product *categories*, not SKU-level identity or counterfeit labels, so results
from this data must never be represented as anti-counterfeit evidence.
"""
import argparse, gzip, hashlib, json, pathlib, shutil, struct, sys
from datetime import datetime, timezone
from urllib.request import urlretrieve

from PIL import Image

BASE_URL = "https://github.com/zalandoresearch/fashion-mnist/raw/master/data/fashion"
FILES = ("train-images-idx3-ubyte.gz", "train-labels-idx1-ubyte.gz", "t10k-images-idx3-ubyte.gz", "t10k-labels-idx1-ubyte.gz")
PRODUCTS = (("product_001_bag", 8), ("product_002_sandal", 5), ("product_003_sneaker", 7), ("product_004_ankle_boot", 9), ("product_005_t_shirt_top", 0))
LABELS = ("t_shirt_top", "trouser", "pullover", "dress", "coat", "sandal", "shirt", "sneaker", "bag", "ankle_boot")


def read_images(path):
    with gzip.open(path, "rb") as stream:
        _, count, rows, cols = struct.unpack(">IIII", stream.read(16))
        return rows, cols, [stream.read(rows * cols) for _ in range(count)]


def read_labels(path):
    with gzip.open(path, "rb") as stream:
        _, count = struct.unpack(">II", stream.read(8))
        return list(stream.read(count))


def save_image(raw, rows, cols, path):
    Image.frombytes("L", (cols, rows), raw).save(path)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def indices(labels, label, count):
    result = [index for index, value in enumerate(labels) if value == label][:count]
    if len(result) != count:
        raise RuntimeError(f"Only found {len(result)} examples for label {label}")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="datasets/eval/products")
    parser.add_argument("--source-cache", default="datasets/public/fashion-mnist")
    args = parser.parse_args()
    root, cache = pathlib.Path(args.dataset), pathlib.Path(args.source_cache)
    existing = [path for path in root.iterdir() if path.name != ".gitkeep"] if root.exists() else []
    if existing:
        sys.exit(f"Refusing to overwrite existing dataset content in {root}.")
    cache.mkdir(parents=True, exist_ok=True)
    for filename in FILES:
        target = cache / filename
        if not target.exists():
            print(f"Downloading {filename}", flush=True)
            urlretrieve(f"{BASE_URL}/{filename}", target)
    rows, cols, train_images = read_images(cache / FILES[0]); train_labels = read_labels(cache / FILES[1])
    test_rows, test_cols, test_images = read_images(cache / FILES[2]); test_labels = read_labels(cache / FILES[3])
    if (rows, cols) != (test_rows, test_cols):
        raise RuntimeError("Fashion-MNIST train/test dimensions do not match")
    root.mkdir(parents=True, exist_ok=True)
    manifest = {"generated_at": datetime.now(timezone.utc).isoformat(), "dataset_source": "Fashion-MNIST (Zalando Research)", "source_url": BASE_URL, "source_file_sha256": {file.name: hashlib.sha256(file.read_bytes()).hexdigest() for file in cache.glob("*.gz")}, "classification": "pipeline validation only; not anti-counterfeit proof", "products": []}
    saved = {}
    for product_id, label in PRODUCTS:
        product = root / product_id
        for folder in ("reference", "genuine", "mismatch"):
            (product / folder).mkdir(parents=True, exist_ok=True)
        references, genuine = indices(train_labels, label, 5), indices(test_labels, label, 10)
        item = {"product_id": product_id, "fashion_mnist_label": label, "fashion_mnist_class": LABELS[label], "reference": [], "genuine": []}
        for number, index in enumerate(references, start=1):
            path = product / "reference" / f"train_{index:05d}.png"
            item["reference"].append({"split": "train", "index": index, "sha256": save_image(train_images[index], rows, cols, path)})
        for number, index in enumerate(genuine, start=1):
            path = product / "genuine" / f"test_{index:05d}.png"
            item["genuine"].append({"split": "test", "index": index, "sha256": save_image(test_images[index], rows, cols, path)})
        saved[product_id] = (label, genuine)
        manifest["products"].append(item)
    for position, (product_id, _) in enumerate(PRODUCTS):
        donor_id, donor_label = PRODUCTS[(position + 1) % len(PRODUCTS)]
        donor_indices = saved[donor_id][1][:5]
        mismatch = []
        for number, index in enumerate(donor_indices, start=1):
            path = root / product_id / "mismatch" / f"test_{index:05d}_{donor_id}.png"
            mismatch.append({"donor_product": donor_id, "donor_class": LABELS[donor_label], "split": "test", "index": index, "sha256": save_image(test_images[index], rows, cols, path)})
        next(item for item in manifest["products"] if item["product_id"] == product_id)["mismatch"] = mismatch
    (root / "PUBLIC_DATASET_MANIFEST.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps({"dataset": str(root), "products": len(PRODUCTS), "evaluation_image_files": 100, "source": manifest["dataset_source"], "classification": manifest["classification"]}, indent=2))


if __name__ == "__main__":
    main()
