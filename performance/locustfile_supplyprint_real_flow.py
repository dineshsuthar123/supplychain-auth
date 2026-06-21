"""Real-image API benchmark. Requires an approved capture dataset; never makes vectors or product records locally."""
import os, pathlib, random, threading, requests
from locust import HttpUser, between, task, events

ROOT = pathlib.Path(os.getenv("BENCHMARK_DATASET", "performance/datasets/sample"))
VALID = {".jpg", ".jpeg", ".png"}
LOCK = threading.Lock(); ENROLL_QUEUE = []
ENROLL_DURING_TEST = os.getenv("BENCHMARK_ENROLL_DURING_TEST", "false").lower() == "true"
def captures(folder): return [p for p in pathlib.Path(folder).glob("*") if p.suffix.lower() in VALID]
def product_id(path): return path.stem.split("__", 1)[0]

class RealImageUser(HttpUser):
    wait_time = between(0.1, 0.4)
    ids, genuine, mismatch, token, initial_events = [], [], [], None, 0
    def on_start(self):
        cls = type(self)
        with LOCK:
            if cls.token is None:
                username, password = os.getenv("BENCHMARK_USERNAME"), os.getenv("BENCHMARK_PASSWORD")
                if not username or not password: raise RuntimeError("BENCHMARK_USERNAME and BENCHMARK_PASSWORD are required")
                login = requests.post(f"{self.host}/auth/login", json={"emailOrUsername": username, "password": password}, timeout=20); login.raise_for_status(); cls.token = login.json()["accessToken"]
                cls.genuine = captures(ROOT / "verify" / "genuine"); cls.mismatch = captures(ROOT / "verify" / "mismatch")
                seeded = captures(ROOT / "enroll")
                cls.ids = [product_id(path) for path in seeded]
                if ENROLL_DURING_TEST: ENROLL_QUEUE.extend(seeded)
                if not cls.genuine: raise RuntimeError("No genuine captures found; see performance/datasets/README.md")
                dashboard = requests.get(f"{self.host}/api/dashboard", headers=self.headers, timeout=20); dashboard.raise_for_status(); initial = dashboard.json(); cls.initial_events = initial.get("verificationsToday", 0)
    @property
    def headers(self): return {"Authorization": f"Bearer {type(self).token}"}
    def upload(self, path, endpoint, name):
        with path.open("rb") as image:
            return self.client.post(endpoint, headers=self.headers, data={"productId": product_id(path)}, files={"image": (path.name, image, "image/png" if path.suffix.lower()=='.png' else "image/jpeg")}, name=name, catch_response=True)
    @task(75)
    def verify_image(self):
        source = self.genuine if not self.mismatch or random.random() < 0.8 else self.mismatch
        with self.upload(random.choice(source), "/api/verify/image", "/api/verify/image [real capture]") as response:
            if response.status_code == 200: response.success()
            else: response.failure(f"HTTP {response.status_code}: {response.text[:160]}")
    @task(10)
    def enroll_image(self):
        with LOCK: path = ENROLL_QUEUE.pop() if ENROLL_QUEUE else None
        if not path: return
        with self.upload(path, "/api/enroll/image", "/api/enroll/image [real capture]") as response:
            if response.status_code == 202: self.ids.append(product_id(path)); response.success()
            else: response.failure(f"HTTP {response.status_code}: {response.text[:160]}")
    @task(10)
    def evidence_log(self):
        if self.ids: self.client.get(f"/api/verify/{random.choice(self.ids)}/log", headers=self.headers, name="/api/verify/{productId}/log")
    @task(5)
    def dashboard(self): self.client.get("/api/dashboard", headers=self.headers, name="/api/dashboard")

@events.test_stop.add_listener
def report(environment, **kwargs):
    try:
        username, password = os.getenv("BENCHMARK_USERNAME"), os.getenv("BENCHMARK_PASSWORD")
        login = requests.post(f"{environment.host}/auth/login", json={"emailOrUsername": username, "password": password}, timeout=20)
        login.raise_for_status()
        summary = requests.get(f"{environment.host}/api/dashboard", headers={"Authorization": f"Bearer {login.json()['accessToken']}"}, timeout=20).json()
        print(f"REAL FLOW SUMMARY: enrolled_products={summary.get('productsAttested')} verification_events_before={RealImageUser.initial_events} verification_events_after={summary.get('verificationsToday')} verification_events_delta={summary.get('verificationsToday', 0) - RealImageUser.initial_events} dataset=sample/prototype unless independently documented otherwise")
    except Exception as error: print(f"Could not read final benchmark summary: {error}")
