"""
Supply Chain Authentication Platform - Performance Testing
Uses current deployed endpoints
"""

import os
from locust import HttpUser, task, constant_pacing
import random

class CurrentAPIUser(HttpUser):
    """Test with currently deployed endpoints"""
    wait_time = constant_pacing(0.1)  # 10 requests/second/user
    host = os.getenv("LOCUST_HOST", "http://localhost:8080")
    
    def on_start(self):
        self.manufacturers = ["APPLE", "SAMSUNG", "NIKE", "ADIDAS", "SONY", "LG", "HP", "DELL"]
        self.registered_serials = []
        self.counter = random.randint(100000, 999999)
    
    def generate_serial_number(self):
        self.counter += 1
        return f"{random.choice(self.manufacturers)[:3]}-{self.counter}"
    
    @task(8)
    def verify_product(self):
        """Verification using current endpoint"""
        if self.registered_serials and random.random() < 0.8:
            serial = random.choice(self.registered_serials)
        else:
            serial = self.generate_serial_number()
        
        # Use the endpoint that's returning 200 in the test
        with self.client.get(
            f"/api/verify/fast/{serial}",
            headers={"Accept": "application/json"},
            catch_response=True,
            name="Verification"
        ) as response:
            # 200 = found, 404 = not found (both are valid)
            if response.status_code in [200, 404]:
                response.success()
            else:
                response.failure(f"HTTP {response.status_code}")
    
    @task(2)
    def register_product(self):
        """Registration using current endpoint"""
        serial = self.generate_serial_number()
        manufacturer = random.choice(self.manufacturers)
        
        payload = {
            "serialNumber": serial,
            "name": f"Product-{serial}",
            "manufacturer": manufacturer,
            "metadataUri": f"ipfs://{serial}"
        }
        
        with self.client.post(
            "/api/products",  # Current endpoint
            json=payload,
            headers={"Content-Type": "application/json"},
            catch_response=True,
            name="Registration"
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
