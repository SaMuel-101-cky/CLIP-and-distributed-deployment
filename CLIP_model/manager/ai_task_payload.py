import numpy as np


def build_task_result_payload(task_id, status, matches=None, error_message=None):
    return {
        "taskId": int(task_id),
        "status": status,
        "errorMessage": error_message,
        "matches": matches or [],
    }


def build_category_matches(probs, photo_ids, description_ids):
    probs_array = np.asarray(probs)
    matches = []

    for photo_index, photo_id in enumerate(photo_ids):
        top_desc_index = int(probs_array[photo_index].argmax())
        matches.append({
            "photoId": int(photo_id),
            "descriptionId": int(description_ids[top_desc_index]),
            "matchType": "CATEGORY",
            "score": float(probs_array[photo_index][top_desc_index]),
            "rankNo": 1,
        })

    return matches


def build_smoke_matches(photo_ids, description_id, match_type, top_k=5):
    matches = []
    for rank, photo_id in enumerate(photo_ids[:top_k], start=1):
        matches.append({
            "photoId": int(photo_id),
            "descriptionId": int(description_id),
            "matchType": match_type,
            "score": float(1 - ((rank - 1) * 0.001)),
            "rankNo": rank,
        })
    return matches


def build_text_search_matches(probs, photo_ids, description_id, top_k=5):
    probs_array = np.asarray(probs)
    query_scores = probs_array[0]
    top_photo_indices = np.argsort(query_scores)[::-1][:top_k]

    matches = []
    for rank, photo_index in enumerate(top_photo_indices, start=1):
        matches.append({
            "photoId": int(photo_ids[photo_index]),
            "descriptionId": int(description_id),
            "matchType": "TEXT_SEARCH",
            "score": float(query_scores[photo_index]),
            "rankNo": rank,
        })

    return matches
