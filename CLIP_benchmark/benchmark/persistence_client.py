import requests


class BenchmarkPersistenceClient:
    def __init__(self, base_url, timeout_seconds=5):
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds

    def _post(self, path, body):
        response = requests.post(f"{self.base_url}{path}", json=body, timeout=self.timeout_seconds)
        response.raise_for_status()

    def create_run(self, run_id, resolved_plan):
        self._post("/benchmark/runs", {"runId": run_id, "status": "PENDING", "resolvedPlanJson": __import__("json").dumps(resolved_plan)})

    def record_sample(self, run_id, sample):
        allowed = ("sequence_no", "warmup", "total_ms", "success", "error_type")
        self._post(f"/benchmark/runs/{run_id}/samples", {key: sample.get(key) for key in allowed} | {"runId": run_id})

    def update_run(self, run_id, status, error_type=None):
        self._post(f"/benchmark/runs/{run_id}", {"status": status, "errorType": error_type})
