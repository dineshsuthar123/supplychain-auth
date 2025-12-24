"""
EXTREME PERFORMANCE TEST - MAXIMUM THROUGHPUT
==============================================
Pushes the system to absolute limits.
Uses all optimization techniques for maximum req/s.
"""

import os
from locust import HttpUser, task, constant_throughput, events, FastHttpUser
import random
import gevent

# Use FastHttpUser for maximum performance (uses geventhttpclient instead of requests)
class ExtremeThroughputUser(FastHttpUser):
    """
    EXTREME: Uses FastHttpUser for 3-5x better performance than regular HttpUser.
    Minimal overhead, maximum requests per second.
    """
    weight = 5
    wait_time = constant_throughput(50)  # 50 requests/second per user!
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    # Connection pooling settings
    connection_timeout = 5.0
    network_timeout = 10.0
    
    def on_start(self):
        self.counter = random.randint(10000000, 99999999)
    
    @task(10)
    def extreme_verify(self):
        """Maximum speed verification"""
        self.counter += 1
        self.client.get(
            f"/api/verify/fast/EXT-{self.counter}",
            name="[EXTREME] Verify"
        )
    
    @task(2)
    def extreme_register(self):
        """Maximum speed registration"""
        self.counter += 1
        self.client.post(
            "/api/products/fast",
            json={"serialNumber": f"EXT-{self.counter}", "name": "X", 
                  "manufacturer": "X", "metadataUri": "x"},
            name="[EXTREME] Register"
        )


class UltraFastVerifyUser(FastHttpUser):
    """
    VERIFY-ONLY: Pure verification throughput test.
    Tests the absolute maximum read capacity.
    """
    weight = 4
    wait_time = constant_throughput(100)  # 100 requests/second per user!
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        self.counter = random.randint(1000000, 9999999)
    
    @task
    def ultra_verify(self):
        self.counter += 1
        self.client.get(f"/api/verify/fast/U-{self.counter}", name="[ULTRA] Verify")


class FullSystemUser(FastHttpUser):
    """
    FULL SYSTEM: Tests complete E2E with FastHttpUser performance.
    Proves correctness at high speed.
    """
    weight = 1
    wait_time = constant_throughput(20)  # 20 req/s per user
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        self.counter = random.randint(500000, 999999)
        self.known = []
    
    @task(5)
    def full_verify(self):
        if self.known:
            serial = random.choice(self.known)
            self.client.get(f"/api/verify/{serial}", name="[FULL] Verify Known")
        else:
            self.counter += 1
            self.client.get(f"/api/verify/fast/F-{self.counter}", name="[FULL] Verify New")
    
    @task(2)
    def full_register(self):
        self.counter += 1
        serial = f"FULL-{self.counter}"
        r = self.client.post(
            "/api/products",
            json={"serialNumber": serial, "name": f"P{self.counter}",
                  "manufacturer": "TEST", "metadataUri": f"ipfs://{self.counter}"},
            name="[FULL] Register"
        )
        if r.status_code == 200:
            self.known.append(serial)
            if len(self.known) > 100:
                self.known = self.known[-50:]


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("\n" + "🔥"*35)
    print("  EXTREME PERFORMANCE TEST COMPLETE")
    print("🔥"*35)
    stats = environment.stats.total
    print(f"\n  Total Requests: {stats.num_requests:,}")
    print(f"  Requests/sec:   {stats.total_rps:.1f}")
    print(f"  Requests/min:   {stats.total_rps * 60:,.0f}")
    print(f"  Failures:       {stats.num_failures}")
    print(f"  Median:         {stats.median_response_time}ms")
    print(f"  P95:            {stats.get_response_time_percentile(0.95)}ms")
    print(f"  P99:            {stats.get_response_time_percentile(0.99)}ms")
    print("🔥"*35 + "\n")
