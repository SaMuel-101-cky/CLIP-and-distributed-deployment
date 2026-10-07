import logging
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

import torch

from utils.setup import configure_logger, inference_log_context
from utils.speed_measurement import run_timed_inference


class InferenceLoggingTest(unittest.TestCase):
    def setUp(self):
        self.logger = logging.getLogger("inference-logging-test")
        self.logger.handlers.clear()
        self.logger.propagate = False

    def tearDown(self):
        for handler in self.logger.handlers[:]:
            handler.close()
            self.logger.removeHandler(handler)

    def test_each_inference_writes_to_a_distinct_date_prefixed_log_file(self):
        with tempfile.TemporaryDirectory() as directory:
            caller_file = Path(directory) / "client.py"
            configure_logger(self.logger, str(caller_file), propagate=False)

            with inference_log_context(
                self.logger,
                str(caller_file),
                now=datetime(2026, 10, 7),
                inference_id="first-run",
            ) as first_path:
                self.logger.info("first inference")

            with inference_log_context(
                self.logger,
                str(caller_file),
                now=datetime(2026, 10, 7),
                inference_id="second-run",
            ) as second_path:
                self.logger.info("second inference")

            self.assertEqual(Path(directory, "logs", "2026_10_07_first-run.log"), first_path)
            self.assertEqual(Path(directory, "logs", "2026_10_07_second-run.log"), second_path)
            self.assertIn("first inference", first_path.read_text(encoding="utf-8"))
            self.assertNotIn("second inference", first_path.read_text(encoding="utf-8"))
            self.assertIn("second inference", second_path.read_text(encoding="utf-8"))

    def test_major_module_timing_logs_start_on_a_separate_line(self):
        with self.assertLogs(self.logger, level="INFO") as captured:
            run_timed_inference(
                tag="encoder_blocks",
                logger=self.logger,
                device=torch.device("cpu"),
                infer_func=lambda: "done",
            )

        self.assertEqual("\n", captured.records[0].getMessage())
        self.assertIn("[encoder_blocks]", captured.output[1])
