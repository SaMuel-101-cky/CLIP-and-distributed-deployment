import unittest
from pathlib import Path
import sys

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from manager.ai_task_payload import (
    build_task_result_payload,
    build_category_matches,
    build_smoke_matches,
    build_text_search_matches,
)


class AiTaskPayloadTest(unittest.TestCase):
    def test_category_matches_use_top_description_per_photo(self):
        probs = np.array([
            [0.1, 0.9],
            [0.8, 0.2],
        ])

        matches = build_category_matches(
            probs=probs,
            photo_ids=[11, 12],
            description_ids=[21, 22],
        )

        self.assertEqual([
            {
                "photoId": 11,
                "descriptionId": 22,
                "matchType": "CATEGORY",
                "score": 0.9,
                "rankNo": 1,
            },
            {
                "photoId": 12,
                "descriptionId": 21,
                "matchType": "CATEGORY",
                "score": 0.8,
                "rankNo": 1,
            },
        ], matches)

    def test_task_result_payload_uses_backend_contract_names(self):
        matches = [{"photoId": 11, "descriptionId": 21}]

        payload = build_task_result_payload(
            task_id=31,
            status="SUCCESS",
            matches=matches,
        )

        self.assertEqual({
            "taskId": 31,
            "status": "SUCCESS",
            "errorMessage": None,
            "matches": matches,
        }, payload)

    def test_smoke_matches_use_first_photos_with_deterministic_scores(self):
        matches = build_smoke_matches(
            photo_ids=[11, 12, 13],
            description_id=21,
            match_type="TEXT_SEARCH",
            top_k=2,
        )

        self.assertEqual([
            {
                "photoId": 11,
                "descriptionId": 21,
                "matchType": "TEXT_SEARCH",
                "score": 1.0,
                "rankNo": 1,
            },
            {
                "photoId": 12,
                "descriptionId": 21,
                "matchType": "TEXT_SEARCH",
                "score": 0.999,
                "rankNo": 2,
            },
        ], matches)

    def test_text_search_matches_rank_top_k_photos_for_query(self):
        probs = np.array([[0.1, 0.7, 0.4, 0.2]])

        matches = build_text_search_matches(
            probs=probs,
            photo_ids=[11, 12, 13, 14],
            description_id=21,
            top_k=3,
        )

        self.assertEqual([
            {
                "photoId": 12,
                "descriptionId": 21,
                "matchType": "TEXT_SEARCH",
                "score": 0.7,
                "rankNo": 1,
            },
            {
                "photoId": 13,
                "descriptionId": 21,
                "matchType": "TEXT_SEARCH",
                "score": 0.4,
                "rankNo": 2,
            },
            {
                "photoId": 14,
                "descriptionId": 21,
                "matchType": "TEXT_SEARCH",
                "score": 0.2,
                "rankNo": 3,
            },
        ], matches)


if __name__ == "__main__":
    unittest.main()
