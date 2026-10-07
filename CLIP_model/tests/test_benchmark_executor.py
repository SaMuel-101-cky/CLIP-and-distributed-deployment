import sys
import unittest
from pathlib import Path

import torch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class BenchmarkExecutorTest(unittest.TestCase):
    def test_uses_the_client_model_and_restores_offload_settings_after_each_sample(self):
        from benchmark.clip_executor import ClipSampleExecutor

        class Plan:
            offload = {"complete_encoders": True}

        class Handler:
            server_ip = "default-host"
            config = {"complete_encoders": False}

        calls = []

        def predict_fn(model, image_paths, queries):
            calls.append((model, list(image_paths), list(queries), handler.server_ip, dict(handler.config)))
            return torch.tensor([[2.0, 4.0]]), None

        handler = Handler()
        model = object()
        executor = ClipSampleExecutor(
            model=model,
            offloader=handler,
            predict_fn=predict_fn,
            default_remote_host="configured-host",
        )

        outcome = executor(Plan(), ["C:/images/cat.jpg"], ["a dog", "a cat"], "autodl-host")

        self.assertTrue(outcome["success"])
        self.assertEqual([(model, ["C:/images/cat.jpg"], ["a dog", "a cat"], "autodl-host", {"complete_encoders": True})], calls)
        self.assertEqual("default-host", handler.server_ip)
        self.assertEqual({"complete_encoders": False}, handler.config)
        self.assertEqual("a cat", outcome["matches"][0]["matches"][0]["query"])
        self.assertEqual(1, outcome["matches"][0]["matches"][0]["rank"])
        self.assertGreater(outcome["matches"][0]["matches"][0]["probability"], 80)
