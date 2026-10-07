import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from time import sleep


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class BenchmarkApiTest(unittest.TestCase):
    def test_model_package_serves_benchmark_routes_from_restricted_directory(self):
        from benchmark.app import create_app

        with TemporaryDirectory() as directory:
            image_directory = Path(directory)
            (image_directory / "cat.jpg").write_bytes(b"image")
            (image_directory / "notes.txt").write_text("not an image")

            response = create_app(image_directory=image_directory).test_client().get("/api/images")

        self.assertEqual(200, response.status_code)
        self.assertEqual([{"name": "cat.jpg"}], response.get_json()["images"])

    def test_creates_polls_and_exports_a_run(self):
        from benchmark.app import create_app

        with TemporaryDirectory() as directory:
            image_directory = Path(directory)
            (image_directory / "sample.png").write_bytes(b"image")
            app = create_app(
                sample_executor=lambda *_: {"success": True, "total_ms": 5.0},
                image_directory=image_directory,
            )
            client = app.test_client()
            created = client.post("/api/experiments", json={
                "mode": "LOCAL_ALL", "batchSize": 1, "warmupRuns": 0, "measuredRuns": 1,
                "imageNames": ["sample.png"], "queries": ["cat"],
            })
            run_id = created.get_json()["runId"]
            exported = client.get(f"/api/experiments/{run_id}/export.json")

        self.assertEqual(202, created.status_code)
        self.assertEqual(200, exported.status_code)
        self.assertNotIn(b"traceback", exported.data.lower())

    def test_serves_selected_image_and_returns_the_measured_match_result(self):
        from benchmark.app import create_app

        matches = [{
            "imageName": "sample.png",
            "matches": [
                {"query": "a cat", "score": 18.2, "probability": 97.1, "rank": 1},
            ],
        }]
        with TemporaryDirectory() as directory:
            image_directory = Path(directory)
            (image_directory / "sample.png").write_bytes(b"image-bytes")
            app = create_app(
                sample_executor=lambda *_: {
                    "success": True,
                    "total_ms": 5.0,
                    "matches": matches,
                },
                image_directory=image_directory,
            )
            client = app.test_client()
            created = client.post("/api/experiments", json={
                "mode": "LOCAL_ALL", "batchSize": 1, "warmupRuns": 0, "measuredRuns": 1,
                "imageNames": ["sample.png"], "queries": ["a cat"],
            })
            run_id = created.get_json()["runId"]
            for _ in range(20):
                run = client.get(f"/api/experiments/{run_id}").get_json()
                if run["status"] == "COMPLETED":
                    break
                sleep(0.01)
            image = client.get("/api/images/sample.png")
            image_body = image.data
            image.close()

        self.assertEqual("COMPLETED", run["status"])
        self.assertEqual(matches, run["results"])
        self.assertEqual(200, image.status_code)
        self.assertEqual(b"image-bytes", image_body)
