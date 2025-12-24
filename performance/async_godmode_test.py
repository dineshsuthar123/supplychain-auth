"""
🔥 TIER-4 GODLY: PURE ASYNC LOAD GENERATOR 🔥

This uses asyncio + aiohttp for maximum throughput WITHOUT CPU saturation.
Unlike Locust which uses greenlets (CPU-bound), this is truly async.

Usage:
    python async_godmode_test.py

Requirements:
    pip install aiohttp

Author: GitHub Copilot
Date: 2025-12-25
"""

import asyncio
import aiohttp
import time
import random
import statistics
from collections import defaultdict
from dataclasses import dataclass
from typing import List

# Configuration
BASE_URL = "http://localhost:8080"
CONCURRENT_CONNECTIONS = 50  # Sweet spot for latency
TOTAL_REQUESTS = 30000
TEST_SERIALS = [f"TEST-{i:04d}" for i in range(1, 10001)]

@dataclass
class Stats:
    latencies: List[float]
    errors: int
    start_time: float
    end_time: float
    
    def percentile(self, p: float) -> float:
        if not self.latencies:
            return 0
        sorted_lat = sorted(self.latencies)
        idx = int(len(sorted_lat) * p / 100)
        return sorted_lat[min(idx, len(sorted_lat) - 1)]
    
    @property
    def rps(self) -> float:
        duration = self.end_time - self.start_time
        return len(self.latencies) / duration if duration > 0 else 0
    
    @property
    def rpm(self) -> float:
        return self.rps * 60
    
    def print_report(self):
        duration = self.end_time - self.start_time
        total = len(self.latencies)
        
        print("\n" + "=" * 80)
        print("🏆 ASYNC GODMODE RESULTS 🏆")
        print("=" * 80)
        print(f"""
    ╔════════════════════════════════════════════════════════════════════════════╗
    ║                        THROUGHPUT METRICS                                   ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║  Total Requests:     {total:>12,}                                      ║
    ║  Requests/sec:       {self.rps:>12,.1f}                                      ║
    ║  Requests/min:       {self.rpm:>12,.0f}                                      ║
    ║  Duration:           {duration:>12.2f}s                                     ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║                        LATENCY METRICS (ms)                                 ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║  Min:                {min(self.latencies)*1000:>12.2f}ms                                    ║
    ║  Median (p50):       {self.percentile(50)*1000:>12.2f}ms                                    ║
    ║  p75:                {self.percentile(75)*1000:>12.2f}ms                                    ║
    ║  p90:                {self.percentile(90)*1000:>12.2f}ms                                    ║
    ║  p95:                {self.percentile(95)*1000:>12.2f}ms  (Target: ≤20ms)               ║
    ║  p99:                {self.percentile(99)*1000:>12.2f}ms  (Target: ≤30ms)               ║
    ║  p99.9:              {self.percentile(99.9)*1000:>12.2f}ms  (Target: ≤60ms)               ║
    ║  p99.99:             {self.percentile(99.99)*1000:>12.2f}ms                                    ║
    ║  Max:                {max(self.latencies)*1000:>12.2f}ms                                    ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║                        RELIABILITY METRICS                                  ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║  Success Rate:       {(total/(total+self.errors))*100 if total else 0:>12.4f}%  (Target: 99.999%)          ║
    ║  Errors:             {self.errors:>12,}                                      ║
    ╚════════════════════════════════════════════════════════════════════════════╝
        """)
        
        # Tier classification
        p99 = self.percentile(99) * 1000
        p999 = self.percentile(99.9) * 1000
        success_rate = (total / (total + self.errors)) * 100 if total else 0
        
        print("=" * 80)
        print("📊 TIER CLASSIFICATION")
        print("=" * 80)
        
        if self.rps >= 50000 and p99 <= 30 and p999 <= 60 and success_rate >= 99.999:
            tier = "TIER-4: GODLY 🏆👑🔥"
        elif self.rps >= 20000 and p99 <= 50 and success_rate >= 99.99:
            tier = "TIER-3: EXPERT 🥇"
        elif self.rps >= 10000 and p99 <= 100 and success_rate >= 99.9:
            tier = "TIER-2: PROFESSIONAL 🥈"
        elif self.rps >= 5000 and success_rate >= 99:
            tier = "TIER-1: COMPETENT 🥉"
        else:
            tier = "TIER-0: BASELINE"
        
        print(f"""
    Classification: {tier}
    
    Score Breakdown:
    ----------------
    RPS:          {self.rps:>10,.0f}  {'✅' if self.rps >= 50000 else '❌'} (need 50,000+)
    p99:          {p99:>10.2f}ms {'✅' if p99 <= 30 else '❌'} (need ≤30ms)
    p99.9:        {p999:>10.2f}ms {'✅' if p999 <= 60 else '❌'} (need ≤60ms)
    Success:      {success_rate:>10.4f}% {'✅' if success_rate >= 99.999 else '❌'} (need 99.999%)
        """)
        print("=" * 80)


async def make_request(session: aiohttp.ClientSession, url: str, latencies: list, errors: list):
    """Make a single async request and record latency"""
    start = time.perf_counter()
    try:
        async with session.get(url) as response:
            await response.read()  # Consume response
            if response.status == 200:
                latency = time.perf_counter() - start
                latencies.append(latency)
            else:
                errors.append(1)
    except Exception as e:
        errors.append(1)


async def warmup(session: aiohttp.ClientSession):
    """Warm up the server and JIT"""
    print("🔥 Warming up server...")
    warmup_tasks = []
    for _ in range(1000):
        serial = random.choice(TEST_SERIALS)
        url = f"{BASE_URL}/api/godmode/v/{serial}"
        warmup_tasks.append(make_request(session, url, [], []))
    await asyncio.gather(*warmup_tasks)
    print("✅ Warmup complete (1000 requests)")


async def run_test():
    """Run the async load test"""
    print("\n" + "=" * 80)
    print("🔥 ASYNC GODMODE PERFORMANCE TEST 🔥")
    print("=" * 80)
    print(f"""
    Configuration:
    - Concurrent connections: {CONCURRENT_CONNECTIONS}
    - Total requests: {TOTAL_REQUESTS:,}
    - Target endpoint: {BASE_URL}/api/godmode/v/{{serial}}
    """)
    
    # Connector with no limit
    connector = aiohttp.TCPConnector(
        limit=CONCURRENT_CONNECTIONS,
        limit_per_host=CONCURRENT_CONNECTIONS,
        keepalive_timeout=60,
        enable_cleanup_closed=True
    )
    
    timeout = aiohttp.ClientTimeout(total=30)
    
    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        # Warmup
        await warmup(session)
        
        # Prepare test
        print(f"\n🚀 Starting {TOTAL_REQUESTS:,} requests with {CONCURRENT_CONNECTIONS} concurrent connections...")
        
        latencies = []
        errors = []
        
        start_time = time.perf_counter()
        
        # Create semaphore to limit concurrency
        sem = asyncio.Semaphore(CONCURRENT_CONNECTIONS)
        
        async def limited_request(url):
            async with sem:
                await make_request(session, url, latencies, errors)
        
        # Create all tasks
        tasks = []
        for i in range(TOTAL_REQUESTS):
            serial = random.choice(TEST_SERIALS)
            url = f"{BASE_URL}/api/godmode/v/{serial}"
            tasks.append(limited_request(url))
        
        # Run all tasks
        await asyncio.gather(*tasks)
        
        end_time = time.perf_counter()
        
        # Print results
        stats = Stats(
            latencies=latencies,
            errors=len(errors),
            start_time=start_time,
            end_time=end_time
        )
        stats.print_report()


if __name__ == "__main__":
    print("🔥 TIER-4 GODLY: ASYNC LOAD GENERATOR 🔥")
    print("=" * 80)
    asyncio.run(run_test())
