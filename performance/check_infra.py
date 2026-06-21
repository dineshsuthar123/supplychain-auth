"""Quick infrastructure connectivity check from the host perspective."""
import socket
import urllib.request
import json

def check_port(host, port, name):
    try:
        s = socket.create_connection((host, port), timeout=3)
        s.close()
        return f"  {name} ({host}:{port}): OK"
    except Exception as e:
        return f"  {name} ({host}:{port}): FAIL - {e}"

def check_http(url, name):
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as resp:
            body = resp.read().decode()
            return f"  {name}: {resp.status} - {body[:100]}"
    except Exception as e:
        return f"  {name}: FAIL - {e}"

print("=== INFRASTRUCTURE CHECK ===")
print(check_port("localhost", 5433, "PostgreSQL"))
print(check_port("localhost", 6380, "Redis"))
print(check_port("localhost", 9095, "Kafka"))
print(check_port("localhost", 10000, "Product-Service"))

print("\n=== ENDPOINT CHECK ===")
print(check_http("http://localhost:10000/api/realworld/ping", "Ping"))
print(check_http("http://localhost:10000/api/realworld/verify/BENCH-0050", "Verify"))
print(check_http("http://localhost:10000/actuator/health", "Health"))

# Test rapid burst
print("\n=== RAPID BURST (10 requests) ===")
import time
times = []
for i in range(10):
    serial = f"BENCH-{(i*10+1):04d}"
    start = time.time()
    try:
        req = urllib.request.Request(f"http://localhost:10000/api/realworld/verify/{serial}")
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode()
            elapsed = (time.time() - start) * 1000
            times.append(elapsed)
            print(f"  {serial}: {elapsed:.0f}ms - {body}")
    except Exception as e:
        elapsed = (time.time() - start) * 1000
        print(f"  {serial}: {elapsed:.0f}ms - FAIL: {e}")

if times:
    print(f"\n  Avg: {sum(times)/len(times):.0f}ms  Min: {min(times):.0f}ms  Max: {max(times):.0f}ms")
