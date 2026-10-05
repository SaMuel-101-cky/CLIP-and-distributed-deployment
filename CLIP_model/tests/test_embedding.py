import sys
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakeModel(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.weight = torch.nn.Parameter(torch.tensor(1.0))

    def encode_image(self, images):
        self.last_images = images
        return torch.tensor([[3.0, 4.0]], device=images.device)

    def encode_text(self, tokens):
        self.last_tokens = tokens
        return torch.tensor([[0.0, 2.0]], device=tokens.device)


class EmbeddingTest(unittest.TestCase):
    def test_normalize_features_returns_unit_vectors(self):
        from utils.embedding import normalize_features

        normalized = normalize_features(torch.tensor([[3.0, 4.0]]))

        self.assertAlmostEqual(1.0, float(torch.linalg.norm(normalized, dim=-1)[0]))

    def test_encode_images_returns_normalized_numpy_vectors(self):
        from utils.embedding import encode_images

        model = FakeModel()
        with patch("utils.embedding.preprocess_images") as preprocess_images:
            preprocess_images.return_value = torch.zeros((1, 3, 224, 224))
            encoded = encode_images(model, ["image.png"])

        np.testing.assert_allclose(np.array([[0.6, 0.8]], dtype=np.float32), encoded)

    def test_encode_texts_returns_normalized_numpy_vectors(self):
        from utils.embedding import encode_texts

        model = FakeModel()
        with patch("model.clip_loader.tokenize") as tokenize:
            tokenize.return_value = torch.ones((1, 77), dtype=torch.long)
            encoded = encode_texts(model, ["cat"])

        np.testing.assert_allclose(np.array([[0.0, 1.0]], dtype=np.float32), encoded)


if __name__ == "__main__":
    unittest.main()
