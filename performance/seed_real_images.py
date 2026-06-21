#!/usr/bin/env python3
"""Enroll actual capture images through the public SupplyPrint API.

No database insert and no embedding generation occurs in this script. Images
are posted to /api/enroll/image, which runs the same server-side ONNX path as
the application UI.
"""
import argparse, os, pathlib, sys, requests

VALID = {".jpg", ".jpeg", ".png"}

def token(base, username, password):
    response = requests.post(f"{base}/auth/login", json={"emailOrUsername": username, "password": password}, timeout=20)
    response.raise_for_status(); return response.json()["accessToken"]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="performance/datasets/sample")
    parser.add_argument("--base-url", default=os.getenv("SUPPLYPRINT_BASE_URL", "http://localhost:10000"))
    parser.add_argument("--username", default=os.getenv("BENCHMARK_USERNAME"))
    parser.add_argument("--password", default=os.getenv("BENCHMARK_PASSWORD"))
    parser.add_argument("--metadata", default='{"source":"benchmark-real-image-upload"}')
    args = parser.parse_args()
    if not args.username or not args.password: sys.exit("BENCHMARK_USERNAME and BENCHMARK_PASSWORD are required; no benchmark account is created automatically.")
    files = sorted(pathlib.Path(args.dataset, "enroll").glob("*"))
    files = [path for path in files if path.suffix.lower() in VALID]
    if not files: sys.exit("No enrollment captures found. See performance/datasets/README.md. Dataset is intentionally empty by default.")
    headers = {"Authorization": f"Bearer {token(args.base_url, args.username, args.password)}"}
    accepted = existing = failed = 0
    for capture in files:
        with capture.open("rb") as image:
            response = requests.post(f"{args.base_url}/api/enroll/image", headers=headers,
                data={"productId": capture.stem, "metadata": args.metadata},
                files={"image": (capture.name, image, "image/jpeg" if capture.suffix.lower() in {'.jpg','.jpeg'} else "image/png")}, timeout=60)
        if response.status_code == 202: accepted += 1; print(f"ENROLLED {capture.stem}")
        elif response.status_code == 409: existing += 1; print(f"EXISTS {capture.stem}")
        else: failed += 1; print(f"FAILED {capture.stem}: HTTP {response.status_code} {response.text[:200]}")
    print(f"Seed complete: enrolled={accepted} existing={existing} failed={failed} dataset=sample/prototype unless independently documented otherwise")
    if failed: sys.exit(1)

if __name__ == "__main__": main()
