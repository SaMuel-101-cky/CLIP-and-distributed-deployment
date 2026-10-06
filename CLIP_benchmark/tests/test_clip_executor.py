import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class ClipExecutorTest(unittest.TestCase):
    def test_lazy_factory_receives_runtime_plan_and_real_inputs(self):
        from benchmark.clip_executor import ClipSampleExecutor
        received = {}
        def factory(plan, remote_host):
            received["plan"] = plan
            received["remote_host"] = remote_host
            return lambda images, queries: (images, queries)
        executor = ClipSampleExecutor(factory)
        outcome = executor("plan", ["D:/sample.png"], ["cat"], "autodl.example")
        self.assertTrue(outcome["success"])
        self.assertEqual(["D:/sample.png"], outcome["image_paths"])
        self.assertEqual("plan", received["plan"])
        self.assertEqual("autodl.example", received["remote_host"])


if __name__ == "__main__": unittest.main()
