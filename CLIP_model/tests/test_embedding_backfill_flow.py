import unittest

import numpy as np

from manager.embedding_backfill import process_embedding_backfill, run_embedding_backfill_task


class FakeVectorStore:
    def __init__(self): self.calls = []
    def upsert_image_embeddings(self, user_id, photo_ids, image_paths, embeddings):
        self.calls.append((user_id, photo_ids, image_paths, embeddings))


class EmbeddingBackfillFlowTest(unittest.TestCase):
    def test_process_backfill_upserts_vectors_and_returns_ready_records(self):
        store = FakeVectorStore()
        result = process_embedding_backfill(store, object(), {
            "taskId": 1, "userId": 7, "photosId": [11, 12], "photosList": ["one.png", "two.png"],
            "embeddingModel": "clip-vit-l-14", "vectorDb": "chroma", "collectionName": "clip_image_embeddings",
        }, lambda model, paths: np.ones((2, 3), dtype=np.float32))
        self.assertEqual((7, [11, 12], ["one.png", "two.png"]), store.calls[0][:3])
        self.assertEqual("SUCCESS", result["status"])
        self.assertEqual(3, result["records"][0]["dim"])

    def test_process_backfill_fails_when_vector_store_unavailable(self):
        result = process_embedding_backfill(None, object(), {
            "taskId": 1, "userId": 7, "photosId": [11], "photosList": ["one.png"],
        }, lambda model, paths: np.ones((1, 3), dtype=np.float32))
        self.assertEqual("FAILED", result["status"])
        self.assertIn("vector store", result["errorMessage"])

    def test_embedding_backfill_process_posts_success_payload(self):
        posted = []
        run_embedding_backfill_task(1, {"taskId": 1, "userId": 7, "photosId": [11], "photosList": ["one.png"]},
                                    FakeVectorStore(), object(), lambda model, paths: np.ones((1, 3)),
                                    "http://backend", None,
                                    post_embedding_result_fn=lambda *args, **kwargs: posted.append(args), smoke_mode=False)
        self.assertEqual(1, posted[0][0])
        self.assertEqual("SUCCESS", posted[0][1]["status"])
