"""
🔥 TIER-4 GODLY PERFORMANCE TEST 🔥

Target Metrics (The Impossible):
================================
- RPS: 50,000 - 100,000+
- p95 latency: ≤ 20ms
- p99 latency: ≤ 30ms
- p99.9 latency: ≤ 60ms
- Success rate: 99.999%

Architecture:
=============
- FastHttpUser (geventhttpclient - C-based)
- Lock-free in-memory storage
- Pre-computed byte responses
- Zero allocation hot paths
- HDR Histogram measurements (via Locust stats)

Usage:
======
1. Start server with: SPRING_PROFILES_ACTIVE=godmode mvn spring-boot:run
2. Run test:
   python -m locust -f locustfile_godmode.py --headless -u 1000 -r 200 -t 60s --host=http://localhost:8080

Author: GitHub Copilot
Date: 2025-12-25
"""

from locust import FastHttpUser, task, constant_throughput, between, events
from locust.runners import MasterRunner
import random
import time
import json
import statistics

# =============================================================================
# GLOBAL METRICS TRACKING (for HDR-like analysis)
# =============================================================================
class MetricsCollector:
    """Collect detailed latency metrics for Tier-4 analysis"""
    def __init__(self):
        self.latencies = []
        self.start_time = None
        self.request_count = 0
        self.error_count = 0
        
    def record(self, latency_ms):
        self.latencies.append(latency_ms)
        self.request_count += 1
        
    def record_error(self):
        self.error_count += 1
        
    def get_percentile(self, p):
        if not self.latencies:
            return 0
        sorted_latencies = sorted(self.latencies)
        idx = int(len(sorted_latencies) * p / 100)
        return sorted_latencies[min(idx, len(sorted_latencies)-1)]
    
    def get_stats(self):
        if not self.latencies:
            return {}
        sorted_lat = sorted(self.latencies)
        return {
            "count": len(sorted_lat),
            "min": sorted_lat[0],
            "max": sorted_lat[-1],
            "mean": statistics.mean(sorted_lat),
            "median": statistics.median(sorted_lat),
            "p50": self.get_percentile(50),
            "p75": self.get_percentile(75),
            "p90": self.get_percentile(90),
            "p95": self.get_percentile(95),
            "p99": self.get_percentile(99),
            "p999": self.get_percentile(99.9),
            "p9999": self.get_percentile(99.99),
            "errors": self.error_count,
            "success_rate": (len(sorted_lat) / (len(sorted_lat) + self.error_count)) * 100 if sorted_lat else 0
        }

# Global metrics collector
metrics = MetricsCollector()

# =============================================================================
# TIER-4 GODLY USERS
# =============================================================================

class GodModeVerifyUser(FastHttpUser):
    """
    🔥 GODLY: Pure verification throughput
    
    Targets the zero-allocation /api/godmode/v/{serial} endpoint.
    Uses pre-computed byte responses.
    """
    weight = 50  # 50% of users
    wait_time = constant_throughput(100)  # 100 req/sec per user
    
    # Pre-computed test serials (matching pre-populated data)
    test_serials = [f"TEST-{i:04d}" for i in range(1, 10001)]
    
    def on_start(self):
        # Warm up
        self.client.get("/api/godmode/ping")
    
    @task(100)
    def verify_godmode(self):
        """Hit the GODLY zero-allocation endpoint"""
        serial = random.choice(self.test_serials)
        start = time.perf_counter()
        
        with self.client.get(
            f"/api/godmode/v/{serial}",
            catch_response=True,
            name="[GODLY] Verify"
        ) as response:
            latency_ms = (time.perf_counter() - start) * 1000
            
            if response.status_code == 200:
                metrics.record(latency_ms)
                response.success()
            else:
                metrics.record_error()
                response.failure(f"Status: {response.status_code}")


class UltraMinimalUser(FastHttpUser):
    """
    🚀 ULTRA: Absolute minimum latency endpoint
    
    Targets /api/godmode/x/{serial} - returns single byte
    """
    weight = 30  # 30% of users
    wait_time = constant_throughput(150)  # 150 req/sec per user - MAXIMUM
    
    test_serials = [f"PROD-{i:04d}" for i in range(1, 10001)]
    
    @task
    def verify_ultra(self):
        """Single byte response endpoint"""
        serial = random.choice(self.test_serials)
        
        with self.client.get(
            f"/api/godmode/x/{serial}",
            catch_response=True,
            name="[ULTRA] Minimal"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status: {response.status_code}")


class PingFloodUser(FastHttpUser):
    """
    ⚡ BASELINE: Ping flood for maximum RPS measurement
    
    Targets /api/godmode/ping - minimal processing
    """
    weight = 10  # 10% of users
    wait_time = constant_throughput(200)  # 200 req/sec per user - ABSOLUTE MAXIMUM
    
    @task
    def ping(self):
        """Health check flood"""
        with self.client.get(
            "/api/godmode/ping",
            catch_response=True,
            name="[PING] Health"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status: {response.status_code}")


class BatchVerifyUser(FastHttpUser):
    """
    📦 BATCH: High-efficiency batch operations
    
    Verifies 100 products in single request
    """
    weight = 10  # 10% of users
    wait_time = constant_throughput(10)  # 10 batches/sec = 1000 verifications/sec
    
    test_serials = [f"TEST-{i:04d}" for i in range(1, 10001)]
    
    @task
    def batch_verify(self):
        """Batch verification - 100 items per request"""
        serials = random.sample(self.test_serials, 100)
        
        with self.client.post(
            "/api/godmode/batch/v",
            json=serials,
            catch_response=True,
            name="[BATCH] Verify 100"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status: {response.status_code}")


# =============================================================================
# EVENT LISTENERS
# =============================================================================

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Initialize metrics on test start"""
    global metrics
    metrics = MetricsCollector()
    metrics.start_time = time.time()
    print("\n" + "="*80)
    print("🔥 TIER-4 GODLY PERFORMANCE TEST STARTING 🔥")
    print("="*80)
    print("""
    Target Metrics:
    ---------------
    RPS:       50,000 - 100,000+
    p95:       ≤ 20ms
    p99:       ≤ 30ms  
    p99.9:     ≤ 60ms
    Success:   99.999%
    """)
    print("="*80 + "\n")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """Print detailed metrics on test stop"""
    stats = metrics.get_stats()
    
    elapsed = time.time() - metrics.start_time if metrics.start_time else 0
    rps = stats.get("count", 0) / elapsed if elapsed > 0 else 0
    
    print("\n" + "="*80)
    print("🏆 TIER-4 GODLY RESULTS 🏆")
    print("="*80)
    
    print(f"""
    ╔════════════════════════════════════════════════════════════════════════════╗
    ║                        THROUGHPUT METRICS                                   ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║  Total Requests:     {stats.get('count', 0):>12,}                                      ║
    ║  Requests/sec:       {rps:>12,.1f}                                      ║
    ║  Requests/min:       {rps * 60:>12,.0f}                                      ║
    ║  Duration:           {elapsed:>12.1f}s                                     ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║                        LATENCY METRICS (ms)                                 ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║  Min:                {stats.get('min', 0):>12.2f}ms                                    ║
    ║  Median (p50):       {stats.get('median', 0):>12.2f}ms                                    ║
    ║  p75:                {stats.get('p75', 0):>12.2f}ms                                    ║
    ║  p90:                {stats.get('p90', 0):>12.2f}ms                                    ║
    ║  p95:                {stats.get('p95', 0):>12.2f}ms  (Target: ≤20ms)               ║
    ║  p99:                {stats.get('p99', 0):>12.2f}ms  (Target: ≤30ms)               ║
    ║  p99.9:              {stats.get('p999', 0):>12.2f}ms  (Target: ≤60ms)               ║
    ║  p99.99:             {stats.get('p9999', 0):>12.2f}ms                                    ║
    ║  Max:                {stats.get('max', 0):>12.2f}ms                                    ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║                        RELIABILITY METRICS                                  ║
    ╠════════════════════════════════════════════════════════════════════════════╣
    ║  Success Rate:       {stats.get('success_rate', 0):>12.4f}%  (Target: 99.999%)          ║
    ║  Errors:             {stats.get('errors', 0):>12,}                                      ║
    ╚════════════════════════════════════════════════════════════════════════════╝
    """)
    
    # Grade the results
    print("\n" + "="*80)
    print("📊 TIER CLASSIFICATION")
    print("="*80)
    
    p99 = stats.get('p99', 999)
    p999 = stats.get('p999', 999)
    success_rate = stats.get('success_rate', 0)
    
    if rps >= 50000 and p99 <= 30 and p999 <= 60 and success_rate >= 99.999:
        tier = "TIER-4: GODLY 🏆👑🔥"
        desc = "People cite your blog. You've achieved the impossible."
    elif rps >= 20000 and p99 <= 50 and success_rate >= 99.99:
        tier = "TIER-3: EXPERT 🥇"
        desc = "Production-ready. Elite performance."
    elif rps >= 10000 and p99 <= 100 and success_rate >= 99.9:
        tier = "TIER-2: PROFESSIONAL 🥈"
        desc = "Above average. Room for optimization."
    elif rps >= 5000 and success_rate >= 99:
        tier = "TIER-1: COMPETENT 🥉"
        desc = "Functional but needs work."
    else:
        tier = "TIER-0: BASELINE"
        desc = "Starting point. Much improvement needed."
    
    print(f"""
    Classification: {tier}
    
    {desc}
    
    Score Breakdown:
    ----------------
    RPS:          {rps:>10,.0f}  {'✅' if rps >= 50000 else '❌'} (need 50,000+)
    p99:          {p99:>10.2f}ms {'✅' if p99 <= 30 else '❌'} (need ≤30ms)
    p99.9:        {p999:>10.2f}ms {'✅' if p999 <= 60 else '❌'} (need ≤60ms)
    Success:      {success_rate:>10.4f}% {'✅' if success_rate >= 99.999 else '❌'} (need 99.999%)
    """)
    
    print("="*80 + "\n")


# =============================================================================
# RUN CONFIGURATION
# =============================================================================
"""
Recommended test configurations:

WARMUP (30 seconds):
  python -m locust -f locustfile_godmode.py --headless -u 100 -r 50 -t 30s

GODLY TEST (60 seconds):
  python -m locust -f locustfile_godmode.py --headless -u 1000 -r 200 -t 60s

EXTREME GODLY (120 seconds, 2000 users):
  python -m locust -f locustfile_godmode.py --headless -u 2000 -r 500 -t 120s

JVM SETTINGS (for server):
  export JAVA_OPTS="-Xmx2g -Xms2g -XX:+UseZGC -XX:+ZGenerational -XX:MaxGCPauseMillis=10 -XX:+AlwaysPreTouch -XX:-UseBiasedLocking"
  
  For Java 17 (no ZGC Generational):
  export JAVA_OPTS="-Xmx2g -Xms2g -XX:+UseZGC -XX:MaxGCPauseMillis=10 -XX:+AlwaysPreTouch -XX:ConcGCThreads=4"
"""
