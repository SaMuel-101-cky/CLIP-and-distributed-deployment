import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class ExecutionPlanTest(unittest.TestCase):
    def test_remote_all_uses_complete_encoders(self):
        from benchmark.execution_plan import ExecutionPlan

        plan = ExecutionPlan.resolve("REMOTE_ALL", batch_size=4, warmup_runs=1, measured_runs=2)

        self.assertTrue(plan.offload["complete_encoders"])
        self.assertFalse(plan.offload["visual_attn"])

    def test_image_remote_leaves_text_blocks_local(self):
        from benchmark.execution_plan import ExecutionPlan

        plan = ExecutionPlan.resolve("TEXT_LOCAL_IMAGE_REMOTE", 1, 0, 1)

        self.assertTrue(plan.offload["vision_conv"])
        self.assertFalse(plan.offload["text_attn"])
