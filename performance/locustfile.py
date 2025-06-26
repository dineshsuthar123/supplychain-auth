"""
Supply Chain Authentication Platform - Performance Testing
Target: 5k+ verifications/minute with <400ms p95 latency
"""

from locust import HttpUser, task, between
import random
import string
import json

class SupplyChainUser(HttpUser):
    wait_time = between(0.1, 0.5)  # High load simulation
    host = "http://k8s-supplych-supplych-01596f8564-48955028.ap-south-1.elb.amazonaws.com"
    
    def on_start(self):
        """Initialize test data"""
        self.manufacturers = ["APPLE", "SAMSUNG", "NIKE", "ADIDAS", "SONY"]
        self.batches = ["BATCH001", "BATCH002", "BATCH003", "BATCH004", "BATCH005"]
        self.registered_serials = []
    
    def generate_serial_number(self, manufacturer, batch):
        """Generate unique serial number"""
        timestamp = random.randint(100000, 999999)
        manufacturer_code = manufacturer[:3].upper()
        return f"{manufacturer_code}-{batch}-{timestamp}"
    
    @task(3)
    def verify_product(self):
        """High-frequency verification testing"""
        # Mix of registered and random serial numbers
        if self.registered_serials and random.random() < 0.7:
            serial = random.choice(self.registered_serials)
        else:
            manufacturer = random.choice(self.manufacturers)
            batch = random.choice(self.batches)
            serial = self.generate_serial_number(manufacturer, batch)
        
        with self.client.get(
            f"/api/verify/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="Product Verification"
        ) as response:
            if response.status_code == 200:
                data = response.json()
                if "verified" in data:
                    response.success()
                else:
                    response.failure("Invalid response format")
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(1)
    def register_product(self):
        """Product registration load testing"""
        manufacturer = random.choice(self.manufacturers)
        batch = random.choice(self.batches)
        serial = self.generate_serial_number(manufacturer, batch)
        
        payload = {
            "serialNumber": serial,
            "name": f"Test Product {random.randint(1000, 9999)}",
            "manufacturer": manufacturer,
            "metadataUri": f"Test metadata for {serial}"
        }
        
        with self.client.post(
            "/api/products",
            json=payload,
            headers={"Content-Type": "application/json"},
            catch_response=True,
            name="Product Registration"
        ) as response:
            if response.status_code == 201:
                data = response.json()
                if "serialNumber" in data:
                    self.registered_serials.append(data["serialNumber"])
                    # Keep list manageable
                    if len(self.registered_serials) > 100:
                        self.registered_serials = self.registered_serials[-50:]
                    response.success()
                else:
                    response.failure("Invalid response format")
            elif response.status_code == 409:
                # Duplicate is expected behavior
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")

class HighVolumeVerificationUser(HttpUser):
    """Specialized user for verification-only load testing"""
    wait_time = between(0.05, 0.1)  # Very high frequency
    host = "http://k8s-supplych-supplych-01596f8564-48955028.ap-south-1.elb.amazonaws.com"
    
    @task(1)
    def rapid_verify(self):
        """Rapid verification for throughput testing"""
        serial = f"TEST-PERF-{random.randint(100000, 999999)}"
        
        with self.client.get(
            f"/api/verify/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="Rapid Verification"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
