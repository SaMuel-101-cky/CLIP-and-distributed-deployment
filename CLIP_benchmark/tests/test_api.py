import sys
import unittest
from tempfile import TemporaryDirectory
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class ApiTest(unittest.TestCase):
    def test_lists_only_supported_images_from_restricted_directory(self):
        from tempfile import TemporaryDirectory
        from pathlib import Path
        from benchmark.app import create_app
        with TemporaryDirectory() as directory:
            root = Path(directory); (root / "cat.jpg").write_bytes(b"x"); (root / "notes.txt").write_text("x")
            response = create_app(image_directory=root).test_client().get("/api/images")
        self.assertEqual(200, response.status_code)
        self.assertEqual(["cat.jpg"], [item["name"] for item in response.get_json()["images"]])
    def test_create_poll_cancel_and_export(self):
        from benchmark.app import create_app
        directory = TemporaryDirectory(); Path(directory.name, "sample.png").write_bytes(b"x")
        app = create_app(sample_executor=lambda *_: {"success": True, "total_ms": 5.0}, image_directory=directory.name)
        client = app.test_client()
        created = client.post("/api/experiments", json={"mode": "LOCAL_ALL", "batchSize": 1, "warmupRuns": 0, "measuredRuns": 1, "imageNames": ["sample.png"], "queries": ["cat"]})
        self.assertEqual(202, created.status_code)
        run_id = created.get_json()["runId"]
        run = client.get(f"/api/experiments/{run_id}")
        self.assertEqual(200, run.status_code)
        self.assertIn(run.get_json()["status"], {"RUNNING", "COMPLETED"})
        exported = client.get(f"/api/experiments/{run_id}/export.json")
        self.assertEqual(200, exported.status_code)
        self.assertNotIn(b"traceback", exported.data.lower())
        directory.cleanup()

    def test_rejects_second_active_run(self):
        from benchmark.app import create_app
        directory = TemporaryDirectory(); Path(directory.name, "sample.png").write_bytes(b"x")
        app = create_app(sample_executor=lambda *_: {"success": True, "total_ms": 1.0}, image_directory=directory.name)
        client = app.test_client()
        payload = {"mode": "LOCAL_ALL", "batchSize": 1, "warmupRuns": 0, "measuredRuns": 50000, "imageNames": ["sample.png"], "queries": ["cat"]}
        self.assertEqual(202, client.post("/api/experiments", json=payload).status_code)
        self.assertEqual(409, client.post("/api/experiments", json=payload).status_code)
        directory.cleanup()


if __name__ == "__main__":
    unittest.main()
