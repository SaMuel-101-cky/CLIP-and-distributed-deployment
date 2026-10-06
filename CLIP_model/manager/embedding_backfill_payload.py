from manager.vector_store import build_photo_vector_id


def validate_backfill_request(data: dict) -> dict:
    if not isinstance(data, dict):
        raise ValueError("embedding backfill payload must be an object")
    required = ("taskId", "userId", "photosId", "photosList")
    if any(data.get(field) is None for field in required):
        raise ValueError("taskId, userId, photosId, and photosList are required")
    photo_ids = [int(photo_id) for photo_id in data["photosId"]]
    photo_paths = [str(path) for path in data["photosList"]]
    if not photo_ids or len(photo_ids) != len(photo_paths):
        raise ValueError("photosId and photosList must have the same nonzero length")
    return {
        "taskId": int(data["taskId"]), "userId": int(data["userId"]),
        "photosId": photo_ids, "photosList": photo_paths,
        "embeddingModel": data.get("embeddingModel"), "vectorDb": data.get("vectorDb"),
        "collectionName": data.get("collectionName"),
    }


def build_embedding_records(photo_ids, embedding_model, vector_db, collection_name, dim) -> list[dict]:
    return [{
        "photoId": int(photo_id), "targetType": "PHOTO", "embeddingModel": embedding_model,
        "vectorDb": vector_db, "collectionName": collection_name,
        "vectorId": build_photo_vector_id(photo_id, embedding_model), "dim": int(dim), "status": "READY",
    } for photo_id in photo_ids]


def build_embedding_result_payload(task_id, status, records=None, error_message=None) -> dict:
    return {"taskId": int(task_id), "status": status, "errorMessage": error_message, "records": records or []}
