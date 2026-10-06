import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from flask import Flask


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class BenchmarkIntegrationTest(unittest.TestCase):
    def test_registers_benchmark_routes_on_the_existing_model_service(self):
        from benchmark.integration import register_benchmark_routes

        with TemporaryDirectory() as directory:
            image_directory = Path(directory)
            (image_directory / "cat.png").write_bytes(b"image")
            app = Flask(__name__)
            register_benchmark_routes(
                app=app,
                model=object(),
                offloader=object(),
                predict_fn=lambda *_: None,
                default_remote_host="127.0.0.1",
                image_directory=image_directory,
            )
            response = app.test_client().get("/api/images")

        self.assertEqual(200, response.status_code)
        self.assertEqual([{"name": "cat.png"}], response.get_json()["images"])
