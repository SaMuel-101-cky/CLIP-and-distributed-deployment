import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class PersistenceClientTest(unittest.TestCase):
    def test_posts_only_bounded_benchmark_fields_to_local_backend(self):
        from benchmark.persistence_client import BenchmarkPersistenceClient

        client = BenchmarkPersistenceClient("http://127.0.0.1:8080")
        with patch("benchmark.persistence_client.requests.post") as post:
            post.return_value.raise_for_status.return_value = None
            client.create_run("run-1", {"mode": "LOCAL_ALL"})
            client.record_sample("run-1", {"sequence_no": 1, "warmup": False, "success": False, "error_type": "REMOTE_TIMEOUT", "error": "secret traceback"})

        self.assertEqual("http://127.0.0.1:8080/benchmark/runs", post.call_args_list[0].args[0])
        sample = post.call_args_list[1].kwargs["json"]
        self.assertNotIn("error", sample)
        self.assertEqual("REMOTE_TIMEOUT", sample["error_type"])


if __name__ == "__main__": unittest.main()
