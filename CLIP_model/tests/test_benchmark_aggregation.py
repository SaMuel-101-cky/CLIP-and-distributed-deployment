import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class AggregationTest(unittest.TestCase):
    def test_excludes_warmups_and_failed_samples(self):
        from benchmark.aggregation import summarize

        result = summarize([
            {"warmup": True, "success": True, "total_ms": 1.0},
            {"warmup": False, "success": True, "total_ms": 10.0},
            {"warmup": False, "success": False, "total_ms": 2.0},
            {"warmup": False, "success": True, "total_ms": 30.0},
        ])

        self.assertEqual(2, result["measured_success_count"])
        self.assertEqual(20.0, result["mean_total_ms"])
        self.assertAlmostEqual(66.6666666667, result["success_rate"], places=8)
