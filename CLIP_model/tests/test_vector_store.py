import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakeCollection:
    def __init__(self):
        self.upserts = []
        self.query_calls = []
        self.get_calls = []
        self.query_response = {
            "metadatas": [[{"photo_id": 11}, {"photo_id": 12}]],
            "distances": [[0.1, 0.4]],
        }
        self.get_response = {"ids": ["photo:11:clip-vit-l-14"]}

    def upsert(self, **kwargs):
        self.upserts.append(kwargs)

    def query(self, **kwargs):
        self.query_calls.append(kwargs)
        return self.query_response

    def get(self, **kwargs):
        self.get_calls.append(kwargs)
        return self.get_response


class FakeClient:
    def __init__(self, collection):
        self.collection = collection
        self.collection_requests = []

    def get_or_create_collection(self, **kwargs):
        self.collection_requests.append(kwargs)
        return self.collection


class VectorStoreTest(unittest.TestCase):
    def test_build_photo_vector_id_is_deterministic(self):
        from manager.vector_store import build_photo_vector_id

        self.assertEqual("photo:12:clip-vit-l-14", build_photo_vector_id(12, "clip-vit-l-14"))

    def test_matches_from_hits_preserve_rank_order_and_score(self):
        from manager.vector_store import VectorSearchHit, build_text_search_matches_from_hits

        matches = build_text_search_matches_from_hits(
            [
                VectorSearchHit(photo_id=12, score=0.8, rank_no=2),
                VectorSearchHit(photo_id=11, score=0.9, rank_no=1),
            ],
            description_id=21,
            allowed_photo_ids=[11, 12],
        )

        self.assertEqual([
            {"photoId": 11, "descriptionId": 21, "matchType": "TEXT_SEARCH", "score": 0.9, "rankNo": 1},
            {"photoId": 12, "descriptionId": 21, "matchType": "TEXT_SEARCH", "score": 0.8, "rankNo": 2},
        ], matches)

    def test_matches_from_hits_ignore_stale_photo_ids(self):
        from manager.vector_store import VectorSearchHit, build_text_search_matches_from_hits

        matches = build_text_search_matches_from_hits(
            [
                VectorSearchHit(photo_id=99, score=0.95, rank_no=1),
                VectorSearchHit(photo_id=11, score=0.9, rank_no=2),
            ],
            description_id=21,
            allowed_photo_ids=[11],
        )

        self.assertEqual([
            {"photoId": 11, "descriptionId": 21, "matchType": "TEXT_SEARCH", "score": 0.9, "rankNo": 1},
        ], matches)

    def test_matches_from_hits_return_only_available_valid_hits(self):
        from manager.vector_store import VectorSearchHit, build_text_search_matches_from_hits

        matches = build_text_search_matches_from_hits(
            [VectorSearchHit(photo_id=11, score=0.9, rank_no=1)],
            description_id=21,
            allowed_photo_ids=[11, 12],
        )

        self.assertEqual(1, len(matches))

    def test_upsert_uses_stable_ids_and_metadata(self):
        from manager.vector_store import ChromaVectorStore, VectorStoreConfig

        collection = FakeCollection()
        store = ChromaVectorStore(
            VectorStoreConfig(
                enabled=True,
                persist_dir="./tmp_chroma",
                collection_name="clip_image_embeddings",
                embedding_model="clip-vit-l-14",
                fallback_enabled=True,
            ),
            client_factory=lambda persist_dir: FakeClient(collection),
        )

        store.upsert_image_embeddings(
            user_id=7,
            photo_ids=[11, 12],
            image_paths=["a.png", "b.png"],
            embeddings=np.array([[0.1, 0.2], [0.3, 0.4]], dtype=np.float32),
        )

        upsert = collection.upserts[0]
        self.assertEqual(["photo:11:clip-vit-l-14", "photo:12:clip-vit-l-14"], upsert["ids"])
        self.assertEqual(7, upsert["metadatas"][0]["user_id"])
        self.assertEqual(11, upsert["metadatas"][0]["photo_id"])
        self.assertEqual("clip-vit-l-14", upsert["metadatas"][0]["embedding_model"])

    def test_query_filters_by_user_and_converts_distances_to_scores(self):
        from manager.vector_store import ChromaVectorStore, VectorStoreConfig, VectorSearchHit

        collection = FakeCollection()
        store = ChromaVectorStore(
            VectorStoreConfig(True, "./tmp_chroma", "clip_image_embeddings", "clip-vit-l-14", True),
            client_factory=lambda persist_dir: FakeClient(collection),
        )

        hits = store.query_images(user_id=7, query_embedding=np.array([0.1, 0.2], dtype=np.float32), top_k=2)

        self.assertEqual({"user_id": 7}, collection.query_calls[0]["where"])
        self.assertEqual([
            VectorSearchHit(photo_id=11, score=0.9, rank_no=1),
            VectorSearchHit(photo_id=12, score=0.6, rank_no=2),
        ], hits)

    def test_has_user_images_uses_user_filter(self):
        from manager.vector_store import ChromaVectorStore, VectorStoreConfig

        collection = FakeCollection()
        store = ChromaVectorStore(
            VectorStoreConfig(True, "./tmp_chroma", "clip_image_embeddings", "clip-vit-l-14", True),
            client_factory=lambda persist_dir: FakeClient(collection),
        )

        self.assertTrue(store.has_user_images(7))
        self.assertEqual({"user_id": 7}, collection.get_calls[0]["where"])


if __name__ == "__main__":
    unittest.main()
