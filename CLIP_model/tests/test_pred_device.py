import unittest
from pathlib import Path
import sys

import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from utils.pred import get_model_device


class PredDeviceTest(unittest.TestCase):
    def test_get_model_device_uses_first_parameter_device(self):
        model = torch.nn.Linear(1, 1)

        self.assertEqual(torch.device("cpu"), get_model_device(model))

    def test_get_model_device_defaults_to_cpu_for_parameterless_model(self):
        model = torch.nn.ReLU()

        self.assertEqual(torch.device("cpu"), get_model_device(model))


if __name__ == "__main__":
    unittest.main()
