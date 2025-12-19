"""
Supply Chain Authentication Platform - COMPREHENSIVE Performance Testing
=======================================================================
This test measures BOTH raw service capacity AND full system correctness.

Test Classes:
1. CapacityUser - Tests architectural throughput limits (fast endpoints)
2. CorrectnessUser - Tests full verification pipeline (standard endpoints)
3. RealisticUser - Mixed workload simulating real usage patterns

Results should be reported as:
- "Service Layer Capacity" for fast endpoint metrics
- "Full System Throughput" for standard endpoint metrics
"""

import os
from locust import HttpUser, task, between, constant_pacing, events
import random
import time
import logging

# Suppress verbose logging for max performance
logging.getLogger("urllib3").setLevel(logging.WARNING)


class CapacityUser(HttpUser):
    """
    CAPACITY TEST: Measures raw architectural throughput.
    Uses optimized /fast endpoints with minimal response overhead.
    Valid for: Infrastructure capacity planning, bottleneck identification.
    """
    weight = 3  # 30% of users
    wait_time = constant_pacing(0.05)  # 20 req/s per user
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        self.counter = random.randint(1000000, 9999999)
        self.registered = []
    
    @task(8)
    def fast_verify(self):
        """Fast verification - tests HTTP stack + caching layer"""
        self.counter += 1
        serial = f"CAP-{self.counter}"
        
        with self.client.get(
            f"/api/verify/fast/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="[Capacity] Fast Verify"
        ) as r:
            if r.status_code in [200]:
                r.success()
            else:
                r.failure(f"HTTP {r.status_code}")
    
    @task(2)
    def fast_register(self):
        """Fast registration - tests write path throughput"""
        self.counter += 1
        serial = f"CAP-REG-{self.counter}"
        
        with self.client.post(
            "/api/products/fast",
            json={"serialNumber": serial, "name": f"P-{serial}", 
                  "manufacturer": "CAPACITY-TEST", "metadataUri": f"ipfs://{serial}"},
            headers={"Content-Type": "application/json"},
            catch_response=True,
            name="[Capacity] Fast Register"
        ) as r:
            if r.status_code in [200, 409]:
                if r.status_code == 200:
                    self.registered.append(serial)
                r.success()
            else:
                r.failure(f"HTTP {r.status_code}")


class CorrectnessUser(HttpUser):
    """
    CORRECTNESS TEST: Measures full verification pipeline.
    Uses standard endpoints with complete response validation.
    Valid for: System correctness, E2E throughput, SLA verification.
    """
    weight = 4  # 40% of users
    wait_time = constant_pacing(0.2)  # 5 req/s per user (realistic)
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        self.counter = random.randint(100000, 999999)
        self.known_serials = []
        self.manufacturers = ["APPLE", "SAMSUNG", "NIKE", "SONY", "LG"]
        # Pre-register some products for verification
        self._seed_products()
    
    def _seed_products(self):
        """Seed known products for correctness testing"""
        for i in range(5):
            serial = f"SEED-{self.counter}-{i}"
            try:
                r = self.client.post(
                    "/api/products",
                    json={"serialNumber": serial, "name": f"Seed Product {i}",
                          "manufacturer": random.choice(self.manufacturers),
                          "metadataUri": f"ipfs://seed-{i}"},
                    headers={"Content-Type": "application/json"},
                    name="[Setup] Seed Product"
                )
                if r.status_code == 200:
                    self.known_serials.append(serial)
            except:
                pass
    
    @task(5)
    def full_verify_known(self):
        """Full verification of KNOWN products - tests complete pipeline"""
        if not self.known_serials:
            return
        
        serial = random.choice(self.known_serials)
        
        with self.client.get(
            f"/api/verify/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="[Correctness] Verify Known"
        ) as r:
            if r.status_code == 200:
                try:
                    data = r.json()
                    # Validate response structure
                    if "verified" in data and "transactionHash" in data:
                        r.success()
                    else:
                        r.failure("Invalid response structure")
                except:
                    r.failure("JSON parse error")
            else:
                r.failure(f"HTTP {r.status_code}")
    
    @task(3)
    def full_register_and_verify(self):
        """Complete register → verify cycle - tests E2E correctness"""
        self.counter += 1
        serial = f"E2E-{self.counter}"
        manufacturer = random.choice(self.manufacturers)
        
        # Step 1: Register
        with self.client.post(
            "/api/products",
            json={"serialNumber": serial, "name": f"E2E Product {self.counter}",
                  "manufacturer": manufacturer, "metadataUri": f"ipfs://e2e-{self.counter}"},
            headers={"Content-Type": "application/json"},
            catch_response=True,
            name="[Correctness] Register"
        ) as r:
            if r.status_code not in [200, 409]:
                r.failure(f"Register failed: {r.status_code}")
                return
            r.success()
        
        # Step 2: Immediate verification (tests consistency)
        with self.client.get(
            f"/api/verify/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="[Correctness] Verify After Register"
        ) as r:
            if r.status_code == 200:
                try:
                    data = r.json()
                    if data.get("verified") == True:
                        self.known_serials.append(serial)
                        r.success()
                    else:
                        r.failure("Product not verified after registration")
                except:
                    r.failure("JSON parse error")
            else:
                r.failure(f"Verify failed: {r.status_code}")
    
    @task(2)
    def verify_unknown(self):
        """Verify unknown serial - tests negative case handling"""
        serial = f"UNKNOWN-{random.randint(1, 999999)}"
        
        with self.client.get(
            f"/api/verify/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="[Correctness] Verify Unknown"
        ) as r:
            if r.status_code == 404:
                r.success()  # Expected behavior
            elif r.status_code == 200:
                data = r.json()
                if data.get("verified") == False:
                    r.success()
                else:
                    r.failure("Unknown product marked as verified")
            else:
                r.failure(f"HTTP {r.status_code}")


class RealisticUser(HttpUser):
    """
    REALISTIC TEST: Simulates actual production usage patterns.
    Mixed workload with realistic think times and behavior.
    Valid for: Production capacity estimation, SLA planning.
    """
    weight = 3  # 30% of users
    wait_time = between(0.5, 2.0)  # Human-like delays
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        self.counter = random.randint(500000, 999999)
        self.my_products = []
        self.manufacturers = ["APPLE", "SAMSUNG", "NIKE", "ADIDAS", "SONY", "LG", "HP", "DELL"]
    
    @task(10)
    def browse_verify(self):
        """User scans a product QR code"""
        if self.my_products and random.random() < 0.7:
            serial = random.choice(self.my_products)
        else:
            serial = f"SCAN-{random.randint(100000, 999999)}"
        
        with self.client.get(
            f"/api/verify/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="[Realistic] Scan Product"
        ) as r:
            if r.status_code in [200, 404]:
                r.success()
            else:
                r.failure(f"HTTP {r.status_code}")
    
    @task(2)
    def register_new_product(self):
        """Manufacturer registers a new product"""
        self.counter += 1
        serial = f"{random.choice(self.manufacturers)[:3]}-{self.counter}"
        
        with self.client.post(
            "/api/products",
            json={
                "serialNumber": serial,
                "name": f"Product {self.counter}",
                "manufacturer": random.choice(self.manufacturers),
                "metadataUri": f"ipfs://QmHash{self.counter}"
            },
            headers={"Content-Type": "application/json"},
            catch_response=True,
            name="[Realistic] Register Product"
        ) as r:
            if r.status_code == 200:
                self.my_products.append(serial)
                if len(self.my_products) > 50:
                    self.my_products = self.my_products[-25:]
                r.success()
            elif r.status_code == 409:
                r.success()
            else:
                r.failure(f"HTTP {r.status_code}")
    
    @task(1)
    def check_product_details(self):
        """User checks full product details"""
        if not self.my_products:
            return
        
        serial = random.choice(self.my_products)
        
        with self.client.get(
            f"/api/products/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="[Realistic] Get Product"
        ) as r:
            if r.status_code in [200, 404]:
                r.success()
            else:
                r.failure(f"HTTP {r.status_code}")


# Performance statistics collector
@events.request.add_listener
def on_request(request_type, name, response_time, response_length, exception, **kwargs):
    """Track request statistics for reporting"""
    pass  # Locust handles this, but hook available for custom metrics


@events.test_stop.add_listener  
def on_test_stop(environment, **kwargs):
    """Print summary when test ends"""
    print("\n" + "="*70)
    print("COMPREHENSIVE PERFORMANCE TEST COMPLETE")
    print("="*70)
    print("\nInterpretation Guide:")
    print("• [Capacity] metrics = Raw service throughput (infrastructure limit)")
    print("• [Correctness] metrics = Full system E2E throughput (real-world limit)")
    print("• [Realistic] metrics = Production-like workload simulation")
    print("\nFor SLA claims, use [Correctness] metrics.")
    print("For capacity planning, use [Capacity] metrics.")
    print("="*70)

