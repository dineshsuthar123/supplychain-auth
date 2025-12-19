"""
Supply Chain Authentication Platform - ULTRA Performance Testing
Target: 12k+ req/min with <400ms p95 latency
Uses new /fast endpoints for maximum throughput
"""

import os
from locust import HttpUser, task, between, constant_pacing
import random
import string
import json

class HighPerformanceUser(HttpUser):
    # Constant pacing for predictable load - 10 requests per second per user
    wait_time = constant_pacing(0.1)
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        """Initialize test data"""
        self.manufacturers = ["APPLE", "SAMSUNG", "NIKE", "ADIDAS", "SONY", "LG", "HP", "DELL"]
        self.batches = ["BATCH001", "BATCH002", "BATCH003", "BATCH004", "BATCH005"]
        self.registered_serials = []
        self.counter = random.randint(100000, 999999)
    
    def generate_serial_number(self):
        """Generate unique serial number - ultra fast"""
        self.counter += 1
        return f"{random.choice(self.manufacturers)[:3]}-{self.counter}"
    
    @task(10)
    def fast_verify(self):
        """FAST verification - minimal response for max throughput"""
        if self.registered_serials and random.random() < 0.8:
            serial = random.choice(self.registered_serials)
        else:
            serial = self.generate_serial_number()
        
        with self.client.get(
            f"/api/verify/fast/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="Fast Verification"
        ) as response:
            if response.status_code in [200, 404]:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(2)
    def fast_register(self):
        """FAST registration - minimal response"""
        serial = self.generate_serial_number()
        manufacturer = random.choice(self.manufacturers)
        
        payload = {
            "serialNumber": serial,
            "name": f"Product-{serial}",
            "manufacturer": manufacturer,
            "metadataUri": f"ipfs://{serial}"
        }
        
        with self.client.post(
            "/api/products/fast",  # Use fast endpoint
            json=payload,
            headers={"Content-Type": "application/json"},
            catch_response=True,
            name="Fast Registration"
        ) as response:
            if response.status_code == 200:
                self.registered_serials.append(serial)
                if len(self.registered_serials) > 200:
                    self.registered_serials = self.registered_serials[-100:]
                response.success()
            elif response.status_code == 409:
                response.success()  # Duplicate is OK
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(1)
    def standard_verify(self):
        """Standard verification for comparison"""
        if self.registered_serials:
            serial = random.choice(self.registered_serials)
        else:
            serial = self.generate_serial_number()
        
        with self.client.get(
            f"/api/verify/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="Standard Verification"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")


class UltraHighThroughputUser(HttpUser):
    """Maximum throughput user - uses only fast endpoints"""
    wait_time = constant_pacing(0.05)  # 20 requests/second/user
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        self.counter = random.randint(1000000, 9999999)
    
    @task(1)
    def ultra_fast_verify(self):
        """Ultra-fast verification"""
        self.counter += 1
        serial = f"ULTRA-{self.counter}"
        
        with self.client.get(
            f"/api/verify/fast/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="Ultra Fast Verify"
        ) as response:
            if response.status_code in [200, 404]:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
