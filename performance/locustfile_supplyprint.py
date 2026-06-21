"""Database-backed SupplyPrint read workload.

This test deliberately does not create synthetic products, embeddings, or
success metrics. On startup every worker discovers product identifiers from
the running service's PostgreSQL-backed dashboard. Each request then reads the
real audit/provenance record for one of those persisted products.

Run only against an approved environment containing non-test data:
  locust -f performance/locustfile_supplyprint.py --headless \
    --host http://localhost:10000 -u 50 -r 5 -t 5m --csv performance/results/db_read
"""

import random
from locust import HttpUser, between, events, task


class DatabaseBackedSupplyPrintUser(HttpUser):
    wait_time = between(0.2, 0.8)
    product_ids: list[str] = []

    def on_start(self):
        if self.product_ids:
            return
        with self.client.get("/api/dashboard", name="/api/dashboard [DB discovery]", catch_response=True) as response:
            if response.status_code != 200:
                response.failure(f"Dashboard discovery failed: HTTP {response.status_code}")
                return
            discovered = [item.get("productId") for item in response.json().get("recentActivity", []) if item.get("productId")]
            type(self).product_ids = list(dict.fromkeys(discovered))
            if not self.product_ids:
                # The aggregate query remains a valid PostgreSQL workload on a
                # new tenant. We intentionally skip provenance reads instead
                # of manufacturing product records for a benchmark.
                print("No persisted products found; running aggregate database reads only.")

    @task(5)
    def read_provenance_record(self):
        if not self.product_ids:
            return
        product_id = random.choice(self.product_ids)
        with self.client.get(f"/api/verify/{product_id}/log", name="/api/verify/{productId}/log [PostgreSQL]", catch_response=True) as response:
            if response.status_code == 200 and response.json().get("productId") == product_id:
                response.success()
            else:
                response.failure(f"Expected persisted record for {product_id}, got HTTP {response.status_code}")

    @task(1)
    def read_operational_snapshot(self):
        with self.client.get("/api/dashboard", name="/api/dashboard [PostgreSQL aggregates]", catch_response=True) as response:
            if response.status_code == 200 and "productsAttested" in response.json():
                response.success()
            else:
                response.failure(f"Expected database snapshot, got HTTP {response.status_code}")


@events.test_start.add_listener
def confirm_database_backed_workload(environment, **_kwargs):
    print("SupplyPrint Locust workload: database-backed provenance reads only; no generated products or embeddings.")
