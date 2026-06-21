import urllib.request
import json

BASE = "http://localhost:10000"

# Test 1: Ping
r = urllib.request.urlopen(f"{BASE}/api/realworld/ping")
print(f"PING: {r.read().decode()}")

# Test 2: Verify registered product
r = urllib.request.urlopen(f"{BASE}/api/realworld/verify/TEST-0001")
print(f"VERIFY (exists): {r.read().decode()}")

# Test 3: Verify unknown product (expect 404)
try:
    r = urllib.request.urlopen(f"{BASE}/api/realworld/verify/FAKE-9999")
    print(f"VERIFY (unknown): {r.read().decode()}")
except urllib.error.HTTPError as e:
    print(f"VERIFY (unknown): {e.code} - {e.read().decode()}")

# Test 4: Metrics
r = urllib.request.urlopen(f"{BASE}/api/realworld/metrics")
print(f"METRICS: {json.dumps(json.loads(r.read().decode()), indent=2)}")

# Test 5: GodMode ping for comparison
r = urllib.request.urlopen(f"{BASE}/api/godmode/ping")
print(f"GODMODE PING: {r.read().decode()}")
