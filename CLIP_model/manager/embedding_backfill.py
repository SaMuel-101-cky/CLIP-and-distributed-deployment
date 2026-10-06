import time

import numpy as np

from manager.embedding_backfill_payload import (
    build_embedding_records,
    build_embedding_result_payload,
    validate_backfill_request,
)


def process_embedding_backfill(vector_store, model, request_data, encode_images_fn, logger=None) -> dict:
    request = validate_backfill_request(request_data)
    task_id = request["taskId"]
    if vector_store is None:
        return build_embedding_result_payload(task_id, "FAILED", error_message="vector store is unavailable")
    try:
        embeddings = np.asarray(encode_images_fn(model, request["photosList"]))
        if embeddings.ndim != 2 or embeddings.shape[0] != len(request["photosId"]):
            raise ValueError("image embedding shape does not match requested photos")
        embedding_model = request["embeddingModel"] or "clip-vit-l-14"
        vector_db = request["vectorDb"] or "chroma"
        collection_name = request["collectionName"] or "clip_image_embeddings"
        vector_store.upsert_image_embeddings(request["userId"], request["photosId"], request["photosList"], embeddings)
        records = build_embedding_records(request["photosId"], embedding_model, vector_db, collection_name, embeddings.shape[1])
        if logger:
            logger.info("Embedding backfill complete: task_id=%s user_id=%s photos=%s dim=%s", task_id, request["userId"], len(records), embeddings.shape[1])
        return build_embedding_result_payload(task_id, "SUCCESS", records=records)
    except Exception as error:
        if logger:
            logger.exception("Embedding backfill failed: task_id=%s", task_id)
        return build_embedding_result_payload(task_id, "FAILED", error_message=str(error))


def run_embedding_backfill_task(task_id, request_data, vector_store, model, encode_images_fn,
                                backend_base_url, callback_token, post_embedding_result_fn,
                                smoke_mode=False, logger=None) -> dict:
    request = validate_backfill_request(request_data)
    started_at = time.monotonic()
    if logger:
        logger.info(
            "event=embedding_backfill.started task_id=%s user_id=%s photo_count=%s",
            task_id, request["userId"], len(request["photosId"]),
        )
    if smoke_mode:
        payload = build_embedding_result_payload(task_id, "FAILED", error_message="Embedding backfill requires real CLIP mode")
    else:
        payload = process_embedding_backfill(vector_store, model, request, encode_images_fn, logger)
    duration_ms = int((time.monotonic() - started_at) * 1000)
    if logger:
        event = "embedding_backfill.completed" if payload["status"] == "SUCCESS" else "embedding_backfill.failed"
        logger.info(
            "event=%s task_id=%s user_id=%s photo_count=%s status=%s duration_ms=%s",
            event, task_id, request["userId"], len(request["photosId"]), payload["status"], duration_ms,
        )
    try:
        post_embedding_result_fn(task_id, payload, backend_base_url, callback_token, logger=logger)
    except Exception as error:
        if logger:
            logger.error("event=embedding_backfill.callback_failed task_id=%s error_type=%s",
                         task_id, type(error).__name__)
    return payload
