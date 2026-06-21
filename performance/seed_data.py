"""Seed 100 benchmark products into the running product-service."""
import urllib.request
import json
import sys

BASE = "http://localhost:10000"

seeded = 0
skipped = 0
for i in range(1, 101):
    serial = f"BENCH-{i:04d}"
    body = json.dumps({
        "serialNumber": serial,
        "name": f"Bench Product {serial}",
        "manufacturer": "BenchCorp",
        "metadataUri": f"ipfs://bench/{serial}",
    }).encode()
    req = urllib.request.Request(
        f"{BASE}/api/products",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    try:
        urllib.request.urlopen(req, timeout=5)
        seeded += 1
    except urllib.error.HTTPError as e:
        if e.code == 409:
            skipped += 1
        else:
            print(f"  ERROR {serial}: {e.code} {e.read().decode()[:100]}")
    except Exception as ex:
        print(f"  ERROR {serial}: {ex}")

print(f"Done: {seeded} seeded, {skipped} already existed.")
