#!/usr/bin/env python3
"""Prepare a traceable public-image *pipeline* validation dataset.

Images are downloaded from Wikimedia Commons categories and every download is
recorded in a manifest.  Mismatch captures are copies of genuine source images
from a different product category; they are never generated or relabeled as
genuine.  This is deliberately unsuitable for anti-counterfeit claims.
"""
import argparse, hashlib, json, pathlib, shutil, sys, time
from datetime import datetime, timezone

import requests
from PIL import Image

API = "https://commons.wikimedia.org/w/api.php"
HEADERS = {"User-Agent": "SupplyPrint-validation/1.0 (local pipeline validation)"}
PRODUCTS = [
    ("product_001_coca_cola", "Coca-Cola bottles"),
    ("product_002_pepsi", "Pepsi bottles"),
    ("product_003_nutella", "Nutella"),
    ("product_004_oreo", "Oreo cookies"),
    ("product_005_lays", "Lay's"),
]
VALID = {".jpg", ".jpeg", ".png"}


def rows(category):
    params = {"action": "query", "format": "json", "generator": "categorymembers", "gcmtitle": f"Category:{category}", "gcmtype": "file", "gcmlimit": 100, "prop": "imageinfo", "iiprop": "url", "iiurlwidth": 1024}
    response = requests.get(API, params=params, headers=HEADERS, timeout=45)
    response.raise_for_status()
    pages = response.json().get("query", {}).get("pages", {}).values()
    for page in sorted(pages, key=lambda value: value["title"]):
        info = page.get("imageinfo", [{}])[0]
        url = info.get("thumburl") or info.get("url")
        if url:
            yield {"title": page["title"], "url": url, "description_url": info.get("descriptionurl")}


def download(item, output):
    for attempt in range(5):
        response = requests.get(item["url"], headers=HEADERS, timeout=90)
        if response.status_code != 429:
            response.raise_for_status()
            break
        time.sleep(4 * (attempt + 1))
    else:
        response.raise_for_status()
    output.write_bytes(response.content)
    try:
        with Image.open(output) as image:
            image.verify()
    except Exception:
        output.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="datasets/eval/products")
    args = parser.parse_args()
    root = pathlib.Path(args.dataset)
    existing = [path for path in root.iterdir() if path.name != ".gitkeep"] if root.exists() else []
    if existing:
        sys.exit(f"Refusing to overwrite existing dataset content in {root}. Move it aside or choose an empty --dataset path.")
    root.mkdir(parents=True, exist_ok=True)
    downloaded = {}
    manifest = {"generated_at": datetime.now(timezone.utc).isoformat(), "source": "Wikimedia Commons public category images", "purpose": "pipeline validation only; not anti-counterfeit proof", "products": []}
    for product_id, category in PRODUCTS:
        product = root / product_id
        for folder in ("reference", "genuine", "mismatch"):
            (product / folder).mkdir(parents=True, exist_ok=True)
        captures = []
        for item in rows(category):
            if len(captures) == 15:
                break
            output = product / "_source" / f"{len(captures)+1:02d}.jpg"
            output.parent.mkdir(exist_ok=True)
            try:
                download(item, output)
            except Exception as exc:
                print(f"SKIP {category}: {item['title'].encode('ascii', 'backslashreplace').decode()}: {exc}")
                continue
            captures.append({**item, "file": str(output.relative_to(root)), "sha256": hashlib.sha256(output.read_bytes()).hexdigest()})
            time.sleep(1.5)
        if len(captures) != 15:
            sys.exit(f"Only downloaded {len(captures)} valid images for {category}; dataset remains incomplete.")
        for index, item in enumerate(captures):
            destination = product / ("reference" if index < 5 else "genuine") / f"{index+1:02d}.jpg"
            shutil.move(root / item["file"], destination)
            item["file"] = str(destination.relative_to(root))
        (product / "_source").rmdir()
        downloaded[product_id] = captures
        manifest["products"].append({"product_id": product_id, "category": category, "reference_count": 5, "genuine_count": 10, "sources": captures})
    for index, (product_id, _) in enumerate(PRODUCTS):
        donor_id = PRODUCTS[(index + 1) % len(PRODUCTS)][0]
        donor = downloaded[donor_id][:5]
        product = root / product_id
        for number, source in enumerate(donor, start=1):
            source_file = root / source["file"]
            destination = product / "mismatch" / f"{number:02d}_{donor_id}.jpg"
            shutil.copy2(source_file, destination)
        next(item for item in manifest["products"] if item["product_id"] == product_id)["mismatch_source_product"] = donor_id
    (root / "PUBLIC_DATASET_MANIFEST.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps({"dataset": str(root), "products": len(PRODUCTS), "unique_public_images": 75, "evaluation_image_files": 100, "classification": manifest["purpose"]}, indent=2))


if __name__ == "__main__":
    main()
