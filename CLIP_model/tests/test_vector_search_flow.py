import sys
import unittest
from pathlib import Path

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from manager.vector_store import VectorSearchHit


class FakeVectorStore:
    def __init__(self, hits=None, error=None):
        self.hits = hits or []
        self.error = error
        self.calls = []

    def query_images(self, user_id, query_embedding, top_k):
        self.calls.append((user_id, query_embedding, top_k))
        if self.error:
            raise self.error
        return self.hits


class CountingPredictor:
    def __init__(self):
        self.calls = 0

    def __call__(self, model, photo_list, descriptions):
        self.calls += 1
        return None, torch.tensor([[0.1, 0.8]])


def fake_encode_texts(model, descriptions):
    return np.array([[0.2, 0.3]], dtype=np.float32)


class VectorSearchFlowTest(unittest.TestCase):
    def test_vector_hits_skip_bruteforce_predictor(self):
        from manager.vector_search import build_text_search_matches_with_vector_store

        predictor = CountingPredictor()
        vector_store = FakeVectorStore(hits=[VectorSearchHit(photo_id=12, score=0.91, rank_no=1)])

        matches = build_text_search_matches_with_vector_store(
            vector_store=vector_store,
            model=object(),
            user_id=7,
            description="cat",
            description_id=21,
            photo_list=["a.png", "b.png"],
            photo_id_list=[11, 12],
            top_k=2,
            fallback_enabled=True,
            encode_texts_fn=fake_encode_texts,
            predict_fn=predictor,
        )

        self.assertEqual(0, predictor.calls)
        self.assertEqual(7, vector_store.calls[0][0])
        self.assertEqual([
            {"photoId": 12, "descriptionId": 21, "matchType": "TEXT_SEARCH", "score": 0.91, "rankNo": 1},
        ], matches)

    def test_vector_error_falls_back_when_enabled(self):
        from manager.vector_search import build_text_search_matches_with_vector_store

        predictor = CountingPredictor()
        matches = build_text_search_matches_with_vector_store(
            vector_store=FakeVectorStore(error=RuntimeError("chroma down")),
            model=object(),
            user_id=7,
            description="cat",
            description_id=21,
            photo_list=["a.png", "b.png"],
            photo_id_list=[11, 12],
            top_k=1,
            fallback_enabled=True,
            encode_texts_fn=fake_encode_texts,
            predict_fn=predictor,
        )

        self.assertEqual(1, predictor.calls)
        self.assertEqual(12, matches[0]["photoId"])

    def test_empty_vector_hits_fall_back_when_enabled(self):
        from manager.vector_search import build_text_search_matches_with_vector_store

        predictor = CountingPredictor()
        matches = build_text_search_matches_with_vector_store(
            vector_store=FakeVectorStore(hits=[]),
            model=object(),
            user_id=7,
            description="cat",
            description_id=21,
            photo_list=["a.png", "b.png"],
            photo_id_list=[11, 12],
            top_k=1,
            fallback_enabled=True,
            encode_texts_fn=fake_encode_texts,
            predict_fn=predictor,
        )

        self.assertEqual(1, predictor.calls)
        self.assertEqual(12, matches[0]["photoId"])

    def test_stale_vector_hits_fall_back_when_no_valid_hits_remain(self):
        from manager.vector_search import build_text_search_matches_with_vector_store

        predictor = CountingPredictor()
        matches = build_text_search_matches_with_vector_store(
            vector_store=FakeVectorStore(hits=[VectorSearchHit(photo_id=99, score=0.99, rank_no=1)]),
            model=object(),
            user_id=7,
            description="cat",
            description_id=21,
            photo_list=["a.png", "b.png"],
            photo_id_list=[11, 12],
            top_k=1,
            fallback_enabled=True,
            encode_texts_fn=fake_encode_texts,
            predict_fn=predictor,
        )

        self.assertEqual(1, predictor.calls)
        self.assertEqual(12, matches[0]["photoId"])


if __name__ == "__main__":
    unittest.main()
