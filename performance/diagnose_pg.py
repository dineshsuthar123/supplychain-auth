"""Diagnose PostgreSQL connection issues."""
import subprocess
import time
import socket

def run_docker_psql(sql):
    result = subprocess.run(
        ["docker", "exec", "supplychain-postgres", "psql", "-U", "postgres", "-d", "supplychain", "-t", "-A", "-c", sql],
        capture_output=True, text=True, timeout=15
    )
    return result.stdout.strip()

print("=== PostgreSQL Connection Diagnosis ===")

try:
    count = run_docker_psql("SELECT count(*) FROM pg_stat_activity;")
    print(f"Total PG connections: {count}")
except Exception as e:
    print(f"PG query failed: {e}")

try:
    maxconn = run_docker_psql("SHOW max_connections;")
    print(f"Max connections: {maxconn}")
except Exception as e:
    print(f"PG query failed: {e}")

print("\n=== Port 5433 TCP test ===")
try:
    s = socket.create_connection(("localhost", 5433), timeout=3)
    s.close()
    print("Port 5433: reachable")
except Exception as e:
    print(f"Port 5433: {e}")

print("\n=== Windows TCP to port 5433 ===")
result = subprocess.run(
    ["powershell", "-c", "Get-NetTCPConnection -RemotePort 5433 -ErrorAction SilentlyContinue | Select-Object State, OwningProcess | Format-Table"],
    capture_output=True, text=True, timeout=10
)
print(result.stdout.strip() if result.stdout.strip() else "No connections found")

# Test app endpoint
print("\n=== App endpoint test ===")
import urllib.request
try:
    with urllib.request.urlopen("http://localhost:10000/api/realworld/ping", timeout=5) as r:
        print(f"Ping: {r.read().decode()}")
except Exception as e:
    print(f"Ping failed: {e}")

try:
    with urllib.request.urlopen("http://localhost:10000/api/realworld/verify/BENCH-0050", timeout=30) as r:
        print(f"Verify: {r.read().decode()}")
except Exception as e:
    print(f"Verify failed: {e}")
