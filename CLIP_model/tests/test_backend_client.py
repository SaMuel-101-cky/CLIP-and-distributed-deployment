import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from manager.backend_client import post_task_result


class BackendClientTest(unittest.TestCase):
    def test_post_task_result_posts_to_task_matches_endpoint(self):
        calls = []

        class Response:
            def raise_for_status(self):
                return None

            def json(self):
                return {"code": 1}

        def fake_post(url, json, headers, timeout):
            calls.append((url, json, headers, timeout))
            return Response()

        payload = {"taskId": 31, "status": "SUCCESS", "matches": []}

        post_task_result(
            task_id=31,
            payload=payload,
            backend_base_url="http://localhost:8080/",
            post=fake_post,
        )

        self.assertEqual([
            ("http://localhost:8080/ai/tasks/31/matches", payload, {}, 10)
        ], calls)

    def test_post_task_result_sends_callback_token_when_configured(self):
        calls = []

        class Response:
            def raise_for_status(self):
                return None

            def json(self):
                return {"code": 1}

        def fake_post(url, json, headers, timeout):
            calls.append(headers)
            return Response()

        post_task_result(
            task_id=31,
            payload={"taskId": 31},
            backend_base_url="http://localhost:8080",
            callback_token="secret",
            post=fake_post,
        )

        self.assertEqual([{"X-AI-Callback-Token": "secret"}], calls)

    def test_post_task_result_raises_when_backend_rejects_payload(self):
        class Response:
            def raise_for_status(self):
                return None

            def json(self):
                return {"code": 0, "message": "bad payload"}

        with self.assertRaisesRegex(RuntimeError, "bad payload"):
            post_task_result(
                task_id=31,
                payload={"taskId": 31},
                backend_base_url="http://localhost:8080",
                post=lambda url, json, headers, timeout: Response(),
            )


if __name__ == "__main__":
    unittest.main()
