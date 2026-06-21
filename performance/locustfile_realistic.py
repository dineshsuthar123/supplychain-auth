"""
Real-World Benchmark: Redis → PostgreSQL → Kafka verification pipeline.

Run:
    # First, start infrastructure and seed data:
    #   docker-compose up -d postgresql redis kafka zookeeper
    #   SPRING_PROFILES_ACTIVE=realistic mvn spring-boot:run
    #   curl -X POST http://localhost:10000/api/products -H 'Content-Type: application/json' \
    #     -d '{"serialNumber":"BENCH-0001","name":"Bench Product","manufacturer":"ACME","metadataUri":"ipfs://test"}'
    #
    # Then run this benchmark with HDR histogram:
    #   pip install locust
    #   locust -f locustfile_realistic.py --headless -u 100 -r 20 -t 60s --host http://localhost:10000

    Compare results with locustfile_godmode.py to see the REAL cost of infrastructure.
"""

from locust import HttpUser, task, between, events
import random
import time
import json

# --- Configuration ---
# These serials MUST exist in the database beforehand (registered via /api/products)
KNOWN_SERIALS = [f"BENCH-{i:04d}" for i in range(1, 101)]  # seed 100 products
UNKNOWN_SERIALS = [f"GHOST-{i:04d}" for i in range(1, 51)]  # never registered


class RealWorldUser(HttpUser):
    """
    Simulates realistic traffic pattern:
      - 70% cache-hit verifications  (known serial, repeated)
      - 15% cache-miss → DB lookups  (known serial, first access)
      - 10% not-found verifications  (unknown serial)
      -  5% product registrations    (write path)
    """
    wait_time = between(0.01, 0.05)  # 20-100 req/sec per user

    def on_start(self):
        self.register_counter = 0
        # Data already seeded in DB and cached in Redis - no warm-up needed

    # ---------- READ: REAL-WORLD verify (Redis → PostgreSQL → Kafka audit) ----------

    @task(70)
    def verify_cache_hit(self):
        """Hot serial – should hit Redis cache after first access."""
        serial = random.choice(KNOWN_SERIALS[:20])  # top 20 are "hot"
        self.client.get(
            f"/api/realworld/verify/{serial}",
            name="/api/realworld/verify/[hot]",
        )

    @task(15)
    def verify_cache_miss(self):
        """Cold serial – first access forces PostgreSQL lookup."""
        serial = random.choice(KNOWN_SERIALS[20:])  # tail are "cold"
        self.client.get(
            f"/api/realworld/verify/{serial}",
            name="/api/realworld/verify/[cold]",
        )

    @task(10)
    def verify_not_found(self):
        """Unknown serial – exercises negative cache path."""
        serial = random.choice(UNKNOWN_SERIALS)
        with self.client.get(
            f"/api/realworld/verify/{serial}",
            name="/api/realworld/verify/[miss]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 404:
                resp.success()  # 404 is expected for unknown serial

    # ---------- WRITE: product registration ----------

    @task(5)
    def register_product(self):
        """Write path: PostgreSQL insert + Redis cache warm-up."""
        self.register_counter += 1
        serial = f"DYN-{int(time.time())}-{self.register_counter}"
        with self.client.post(
            "/api/products",
            json={
                "serialNumber": serial,
                "name": f"Dynamic Product {serial}",
                "manufacturer": "BenchCorp",
                "metadataUri": f"ipfs://dyn/{serial}",
            },
            name="/api/products [register]",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 201, 409):
                resp.success()

    # ---------- BASELINE: ping (framework overhead only) ----------

    @task(5)
    def ping_baseline(self):
        """Measure pure framework overhead for comparison."""
        self.client.get("/api/realworld/ping", name="/api/realworld/ping")

    # ---------- COMPARISON: godmode ping (for side-by-side) ----------

    @task(5)
    def godmode_ping(self):
        """GodMode ping for direct comparison in the same test run."""
        self.client.get("/api/godmode/ping", name="/api/godmode/ping [compare]")


# --- Event hooks for summary ---

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    stats = environment.runner.stats
    print("\n" + "=" * 70)
    print("REAL-WORLD vs GODMODE COMPARISON")
    print("=" * 70)

    for name in [
        "/api/realworld/verify/[hot]",
        "/api/realworld/verify/[cold]",
        "/api/realworld/verify/[miss]",
        "/api/godmode/ping [compare]",
    ]:
        s = stats.get(name, "GET")
        if s and s.num_requests > 0:
            print(
                f"  {name:<45} "
                f"p50={s.get_response_time_percentile(0.5):>6.0f}ms  "
                f"p99={s.get_response_time_percentile(0.99):>6.0f}ms  "
                f"RPS={s.current_rps:>6.0f}  "
                f"err={s.num_failures}"
            )
    print("=" * 70)
    print("If RealWorld p99 is 15-50ms, that is ELITE real-world performance.")
    print("If GodMode p99 is 2-10ms, that is the in-memory baseline.")
    print("The delta between them is the HONEST cost of real infrastructure.")
    print("=" * 70)
