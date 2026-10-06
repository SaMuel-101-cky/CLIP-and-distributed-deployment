import base64
import io
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class OffloadProtocolTest(unittest.TestCase):
    def test_rejects_missing_or_invalid_token_before_decoding_payload(self):
        from utils.offload_protocol import execute_tensor_rpc

        decoded = []

        def decode(_payload):
            decoded.append(True)
            return {}

        missing = execute_tensor_rpc(
            headers={}, payload={"data": "not-even-base64"}, expected_token="secret",
            decode=decode, infer=lambda _: torch.tensor([1]), encode=lambda _: "result",
        )
        invalid = execute_tensor_rpc(
            headers={"X-Offload-Token": "wrong"}, payload={"data": "not-even-base64"}, expected_token="secret",
            decode=decode, infer=lambda _: torch.tensor([1]), encode=lambda _: "result",
        )

        self.assertEqual(401, missing.status_code)
        self.assertEqual(401, invalid.status_code)
        self.assertEqual([], decoded)

    def test_valid_request_returns_correlated_timing_and_payload_metadata(self):
        from utils.offload_protocol import execute_tensor_rpc

        response = execute_tensor_rpc(
            headers={"X-Offload-Token": "secret"},
            payload={"request_id": "req-123", "data": "payload"},
            expected_token="secret",
            decode=lambda raw: {"decoded": raw},
            infer=lambda decoded: torch.tensor([decoded["decoded"] == "payload"]),
            encode=lambda _: "encoded-output",
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual("req-123", response.body["request_id"])
        self.assertEqual("encoded-output", response.body["output"])
        self.assertEqual(7, response.body["payload_bytes"]["request"])
        self.assertGreaterEqual(response.body["payload_bytes"]["response"], len("encoded-output"))
        self.assertEqual(
            {"remote_decode_ms", "remote_infer_ms", "remote_encode_ms"},
            set(response.body["timings"]),
        )

    def test_handler_emits_metrics_without_payload_or_token(self):
        from utils.offloader import OffloadHandler

        encoded = io.BytesIO()
        torch.save({"output": torch.tensor([3.0])}, encoded)
        response_body = {
            "output": base64.b64encode(encoded.getvalue()).decode(),
            "request_id": "req-1",
            "timings": {
                "remote_decode_ms": 1.0,
                "remote_infer_ms": 2.0,
                "remote_encode_ms": 3.0,
            },
            "payload_bytes": {"request": 4, "response": 8},
        }

        class Response:
            def raise_for_status(self):
                return None

            def json(self):
                return response_body

        metrics = []
        handler = OffloadHandler(
            server_ip="127.0.0.1", server_port=5000, config={}, token="secret", metrics_sink=metrics.append,
        )
        with patch("utils.offloader.requests.post", return_value=Response()) as post:
            output = handler.call_remote("attention", {"x": torch.tensor([1.0])}, torch.device("cpu"))

        self.assertEqual([3.0], output.tolist())
        self.assertEqual("secret", post.call_args.kwargs["headers"]["X-Offload-Token"])
        self.assertEqual(1, len(metrics))
        event = metrics[0]
        self.assertEqual("attention", event.endpoint)
        self.assertEqual(4, event.request_bytes)
        self.assertEqual(8, event.response_bytes)
        self.assertEqual(2.0, event.remote_infer_ms)
        self.assertFalse(hasattr(event, "payload"))
        self.assertFalse(hasattr(event, "token"))

    def test_handler_preserves_complete_encoder_feature_mapping(self):
        from utils.offloader import OffloadHandler

        encoded = io.BytesIO()
        torch.save({"image_features": torch.tensor([[1.0]]), "text_features": torch.tensor([[2.0]])}, encoded)

        class Response:
            def raise_for_status(self):
                return None

            def json(self):
                return {"output": base64.b64encode(encoded.getvalue()).decode(), "timings": {}, "payload_bytes": {}}

        handler = OffloadHandler("127.0.0.1", 5000, {}, token="secret")
        with patch("utils.offloader.requests.post", return_value=Response()):
            result = handler.call_remote("complete_encoders", {"image": torch.tensor([1.0])}, torch.device("cpu"))

        self.assertEqual([1.0], result["image_features"].flatten().tolist())
        self.assertEqual([2.0], result["text_features"].flatten().tolist())

    def test_execution_plan_overrides_process_start_config(self):
        from utils.offloader import OffloadHandler

        class Plan:
            offload = {"visual_attn": True}

        handler = OffloadHandler("127.0.0.1", 5000, {"visual_attn": False}, execution_plan=Plan())

        self.assertTrue(handler.should_offload("visual_attn"))


if __name__ == "__main__":
    unittest.main()
