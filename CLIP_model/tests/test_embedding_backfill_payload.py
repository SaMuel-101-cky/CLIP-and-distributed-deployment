import unittest

from manager.embedding_backfill_payload import (
    build_embedding_records,
    build_embedding_result_payload,
    validate_backfill_request,
)


class EmbeddingBackfillPayloadTest(unittest.TestCase):
    def test_validate_backfill_request_rejects_missing_required_fields(self):
        with self.assertRaises(ValueError):
            validate_backfill_request({"taskId": 1})

    def test_validate_backfill_request_rejects_mismatched_photo_lengths(self):
        with self.assertRaises(ValueError):
            validate_backfill_request({"taskId": 1, "userId": 7, "photosId": [11], "photosList": []})

    def test_build_embedding_records_uses_photo_vector_ids_and_dim(self):
        self.assertEqual([{
            "photoId": 11, "targetType": "PHOTO", "embeddingModel": "clip-vit-l-14",
            "vectorDb": "chroma", "collectionName": "clip_image_embeddings",
            "vectorId": "photo:11:clip-vit-l-14", "dim": 768, "status": "READY",
        }], build_embedding_records([11], "clip-vit-l-14", "chroma", "clip_image_embeddings", 768))

    def test_build_embedding_result_payload_matches_backend_contract(self):
        self.assertEqual({"taskId": 1, "status": "FAILED", "errorMessage": "unavailable", "records": []},
                         build_embedding_result_payload(1, "FAILED", error_message="unavailable"))
