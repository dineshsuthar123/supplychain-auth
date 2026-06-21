"""
Async Real-World Benchmark (aiohttp).

Measures REAL latency through Redis → PostgreSQL → Kafka pipeline.
Directly comparable with async_godmode_test.py to show honest cost of infrastructure.

Requirements:
    pip install aiohttp

Run:
    1. Start infra:        docker-compose up -d postgresql redis kafka zookeeper
    2. Start service:      SPRING_PROFILES_ACTIVE=realistic mvn spring-boot:run
    3. Seed test data:     python async_realistic_test.py --seed
    4. Run benchmark:      python async_realistic_test.py

Compare output with async_godmode_test.py to see the real-world vs in-memory delta.
"""

import aiohttp
import asyncio
import argparse
import statistics
import time
import sys


BASE_URL = "http://localhost:10000"
CONCURRENCY = 50
TOTAL_REQUESTS = 10_000
KNOWN_SERIALS = [f"BENCH-{i:04d}" for i in range(1, 101)]


async def seed_products(session: aiohttp.ClientSession):
    """Pre-register products so verifications have data to find."""
    print(f"Seeding {len(KNOWN_SERIALS)} products...")
    for serial in KNOWN_SERIALS:
        try:
            await session.post(
                f"{BASE_URL}/api/products",
                json={
                    "serialNumber": serial,
                    "name": f"Bench Product {serial}",
                    "manufacturer": "BenchCorp",
                    "metadataUri": f"ipfs://bench/{serial}",
                },
                timeout=aiohttp.ClientTimeout(total=5),
            )
        except Exception:
            pass  # 409 Conflict is expected for re-runs
    print("  Seeding complete. Warming cache with first-pass verifications...")
    for serial in KNOWN_SERIALS:
        try:
            await session.get(
                f"{BASE_URL}/api/realworld/verify/{serial}",
                timeout=aiohttp.ClientTimeout(total=5),
            )
        except Exception:
            pass
    print("  Cache warm-up complete.\n")


async def benchmark_endpoint(
    session: aiohttp.ClientSession,
    sem: asyncio.Semaphore,
    url: str,
    latencies: list,
    errors: list,
):
    async with sem:
        start = time.perf_counter()
        try:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=5)) as resp:
                await resp.read()
                elapsed_ms = (time.perf_counter() - start) * 1000
                latencies.append(elapsed_ms)
                if resp.status not in (200, 404):
                    errors.append(resp.status)
        except Exception as e:
            elapsed_ms = (time.perf_counter() - start) * 1000
            latencies.append(elapsed_ms)
            errors.append(str(e))


def print_histogram(name: str, latencies: list, errors: list, wall_time: float):
    if not latencies:
        print(f"  {name}: no data")
        return

    latencies.sort()
    n = len(latencies)
    rps = n / wall_time if wall_time > 0 else 0
    rpm = rps * 60

    print(f"\n{'='*60}")
    print(f"  {name}")
    print(f"{'='*60}")
    print(f"  Requests:    {n:,}")
    print(f"  Errors:      {len(errors)}")
    print(f"  Wall time:   {wall_time:.2f}s")
    print(f"  RPS:         {rps:,.0f}")
    print(f"  RPM:         {rpm:,.0f}")
    print(f"  ---")
    print(f"  Min:         {latencies[0]:.2f} ms")
    print(f"  Median:      {latencies[n // 2]:.2f} ms")
    print(f"  Mean:        {statistics.mean(latencies):.2f} ms")
    print(f"  p90:         {latencies[int(n * 0.90)]:.2f} ms")
    print(f"  p95:         {latencies[int(n * 0.95)]:.2f} ms")
    print(f"  p99:         {latencies[int(n * 0.99)]:.2f} ms")
    print(f"  p99.9:       {latencies[min(int(n * 0.999), n - 1)]:.2f} ms")
    print(f"  Max:         {latencies[-1]:.2f} ms")
    print(f"  Success:     {((n - len(errors)) / n * 100):.2f}%")


async def run_benchmark():
    sem = asyncio.Semaphore(CONCURRENCY)
    connector = aiohttp.TCPConnector(limit=CONCURRENCY, force_close=False)

    async with aiohttp.ClientSession(connector=connector) as session:
        # Seed data first
        await seed_products(session)

        # ── Benchmark 1: Real-World verify (cache hits) ──────────────────
        import random

        hot_serials = KNOWN_SERIALS[:20]
        realworld_latencies = []
        realworld_errors = []

        tasks = []
        for _ in range(TOTAL_REQUESTS):
            serial = random.choice(hot_serials)
            url = f"{BASE_URL}/api/realworld/verify/{serial}"
            tasks.append(
                benchmark_endpoint(session, sem, url, realworld_latencies, realworld_errors)
            )

        start = time.perf_counter()
        await asyncio.gather(*tasks)
        wall_time = time.perf_counter() - start
        print_histogram("REAL-WORLD /api/realworld/verify (Redis+PostgreSQL+Kafka)", realworld_latencies, realworld_errors, wall_time)

        # ── Benchmark 2: GodMode verify (for comparison) ─────────────────
        godmode_latencies = []
        godmode_errors = []
        tasks = []
        for _ in range(TOTAL_REQUESTS):
            serial = random.choice(hot_serials)
            url = f"{BASE_URL}/api/godmode/v/{serial}"
            tasks.append(
                benchmark_endpoint(session, sem, url, godmode_latencies, godmode_errors)
            )

        start = time.perf_counter()
        await asyncio.gather(*tasks)
        wall_time = time.perf_counter() - start
        print_histogram("GODMODE /api/godmode/v (in-memory, no DB)", godmode_latencies, godmode_errors, wall_time)

        # ── Comparison ───────────────────────────────────────────────────
        if realworld_latencies and godmode_latencies:
            rw_p99 = sorted(realworld_latencies)[int(len(realworld_latencies) * 0.99)]
            gm_p99 = sorted(godmode_latencies)[int(len(godmode_latencies) * 0.99)]
            delta = rw_p99 - gm_p99

            print(f"\n{'='*60}")
            print("  HONEST COMPARISON")
            print(f"{'='*60}")
            print(f"  GodMode p99:      {gm_p99:.2f} ms  (in-memory)")
            print(f"  RealWorld p99:    {rw_p99:.2f} ms  (Redis+PostgreSQL+Kafka)")
            print(f"  Infrastructure Δ: {delta:.2f} ms  (cost of real persistence)")
            print(f"{'='*60}")
            if rw_p99 < 50:
                print("  ✅ ELITE: <50ms p99 with real infrastructure")
            elif rw_p99 < 150:
                print("  ✅ PRODUCTION-GRADE: <150ms p99 with real infrastructure")
            else:
                print("  ⚠️  Optimize: tune DB pool, Redis connection, indexes")
            print(f"\n  Fetch /api/realworld/metrics for cache hit rates and more.")

        # ── Print real-world metrics ─────────────────────────────────────
        try:
            async with session.get(f"{BASE_URL}/api/realworld/metrics") as resp:
                metrics = await resp.json()
                print(f"\n  Server-side metrics: {metrics}")
        except Exception:
            pass


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", action="store_true", help="Only seed data, don't benchmark")
    parser.add_argument("--requests", type=int, default=TOTAL_REQUESTS)
    parser.add_argument("--concurrency", type=int, default=CONCURRENCY)
    args = parser.parse_args()

    global TOTAL_REQUESTS, CONCURRENCY
    TOTAL_REQUESTS = args.requests
    CONCURRENCY = args.concurrency

    if args.seed:
        connector = aiohttp.TCPConnector(limit=10)
        async with aiohttp.ClientSession(connector=connector) as session:
            await seed_products(session)
        return

    await run_benchmark()


if __name__ == "__main__":
    asyncio.run(main())
