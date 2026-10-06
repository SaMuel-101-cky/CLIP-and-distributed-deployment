import torch

from manager.ai_task_payload import build_text_search_matches
from manager.vector_store import build_text_search_matches_from_hits


def _build_fallback_matches(
    model,
    description,
    description_id,
    photo_list,
    photo_id_list,
    top_k,
    predict_fn,
):
    _, logits_per_text = predict_fn(model, photo_list, [description])
    probs = torch.softmax(logits_per_text, dim=-1)
    return build_text_search_matches(
        probs.cpu().numpy(),
        photo_id_list,
        description_id,
        top_k=top_k,
    )


def build_text_search_matches_with_vector_store(
    vector_store,
    model,
    user_id,
    description,
    description_id,
    photo_list,
    photo_id_list,
    top_k,
    fallback_enabled,
    encode_texts_fn,
    predict_fn,
):
    try:
        if vector_store is not None:
            query_embedding = encode_texts_fn(model, [description])[0]
            hits = vector_store.query_images(user_id, query_embedding, top_k)
            matches = build_text_search_matches_from_hits(
                hits,
                description_id,
                photo_id_list,
            )
            if matches:
                return matches
    except Exception:
        if not fallback_enabled:
            raise

    if not fallback_enabled:
        return []

    return _build_fallback_matches(
        model,
        description,
        description_id,
        photo_list,
        photo_id_list,
        top_k,
        predict_fn,
    )
