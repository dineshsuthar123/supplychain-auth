#!/usr/bin/env python3
"""Validate a labeled physical-capture dataset without modifying it."""
import argparse, json, pathlib, sys
from datetime import datetime, timezone

VALID = {".jpg", ".jpeg", ".png"}

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--dataset", default="datasets/eval/products"); parser.add_argument("--json", default="reports/dataset-validation/latest.json"); parser.add_argument("--markdown", default="reports/dataset-validation/latest.md")
    args = parser.parse_args(); root = pathlib.Path(args.dataset); products = [] if not root.exists() else sorted(path for path in root.iterdir() if path.is_dir())
    report = {"generated_at": datetime.now(timezone.utc).isoformat(), "dataset": str(root), "products": [], "errors": [], "warnings": []}
    if not products: report["warnings"].append("No product directories found; add controlled physical-capture images before evaluation.")
    for product in products:
        row = {"product_id": product.name, "reference": 0, "genuine": 0, "mismatch": 0, "layout": "flat", "invalid_files": [], "unreadable_files": []}
        genuine_root = product / "genuine"
        top_level = (product / "reference").exists()
        if top_level:
            row["layout"] = "top-level-separated"
            folders = (("reference", product / "reference"), ("genuine", genuine_root), ("mismatch", product / "mismatch"))
        elif not genuine_root.exists(): report["errors"].append(f"{product.name}: missing genuine/ directory")
        elif (genuine_root / "reference").exists() or (genuine_root / "verify").exists():
            row["layout"] = "separated"
            folders = (("reference", genuine_root / "reference"), ("genuine", genuine_root / "verify"), ("mismatch", product / "mismatch"))
        else:
            folders = (("genuine", genuine_root), ("mismatch", product / "mismatch"))
        if top_level or genuine_root.exists():
            for label, folder in folders:
                if not folder.exists(): report["errors"].append(f"{product.name}: missing {label}/ directory"); continue
                for image in folder.iterdir():
                    if not image.is_file(): continue
                    if image.suffix.lower() not in VALID: row["invalid_files"].append(str(image)); continue
                    try:
                        from PIL import Image
                        with Image.open(image) as capture: capture.verify()
                        row[label] += 1
                    except Exception as exc: row["unreadable_files"].append(f"{image}: {exc}")
        if row["invalid_files"]: report["warnings"].append(f"{product.name}: ignored unsupported files")
        if row["unreadable_files"]: report["errors"].append(f"{product.name}: unreadable image files")
        if row["layout"] in {"separated", "top-level-separated"} and (row["reference"] < 5 or row["genuine"] < 10 or row["mismatch"] < 5): report["warnings"].append(f"{product.name}: fewer than target 5 reference, 10 genuine, and 5 mismatch images")
        if row["layout"] == "flat" and (row["genuine"] < 20 or row["mismatch"] < 10): report["warnings"].append(f"{product.name}: flat genuine/ layout needs 20 genuine and 10 mismatch images")
        report["products"].append(row)
    count = len(products)
    def at_least(row, reference, genuine, mismatch):
        return row["mismatch"] >= mismatch and ((row["layout"] in {"separated", "top-level-separated"} and row["reference"] >= reference and row["genuine"] >= genuine) or (row["layout"] == "flat" and row["genuine"] >= reference + genuine))
    minimum = count >= 5 and all(at_least(x, 5, 10, 5) for x in report["products"])
    strong = count >= 20 and all(at_least(x, 30, 30, 30) for x in report["products"])
    report["classification"] = "stronger-validation-scale" if strong else "prototype-scale" if minimum else "insufficient"
    if report["classification"] != "stronger-validation-scale": report["warnings"].append("Dataset is not sufficient for field-grade anti-counterfeit claims.")
    for output in (pathlib.Path(args.json), pathlib.Path(args.markdown)): output.parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.json).write_text(json.dumps(report, indent=2))
    lines=["# Dataset validation", "", f"Dataset: `{root}`", "", f"Classification: **{report['classification']}**", "", "| Product | Layout | Reference | Genuine | Mismatch |", "|---|---|---:|---:|---:|"]
    lines += [f"| {row['product_id']} | {row['layout']} | {row['reference']} | {row['genuine']} | {row['mismatch']} |" for row in report["products"]]
    lines += ["", "## Warnings"] + [f"- {item}" for item in report["warnings"]] + ["", "## Errors"] + [f"- {item}" for item in report["errors"]]
    pathlib.Path(args.markdown).write_text("\n".join(lines)+"\n")
    print(json.dumps(report, indent=2))
    if report["errors"]: sys.exit(1)
if __name__ == "__main__": main()
