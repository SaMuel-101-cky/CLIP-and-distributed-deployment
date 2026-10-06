from dataclasses import dataclass

import torch

from manager.ai_task_payload import build_text_search_matches
from manager.vector_store import build_text_search_matches_from_hits


@dataclass(frozen=True)
class VectorSearchResult:
    matches: list[dict]
    source: str
    fallback_reason: str | None = None
    vector_hit_count: int = 0


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


def build_text_search_result_with_vector_store(
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
    fallback_reason = None
    vector_hit_count = 0

    try:
        if vector_store is not None:
            query_embedding = encode_texts_fn(model, [description])[0]
            hits = vector_store.query_images(user_id, query_embedding, top_k)
            vector_hit_count = len(hits)
            matches = build_text_search_matches_from_hits(
                hits,
                description_id,
                photo_id_list,
            )
            if matches:
                return VectorSearchResult(
                    matches=matches,
                    source="CHROMA",
                    vector_hit_count=vector_hit_count,
                )
            fallback_reason = "vector_store_empty" if not hits else "vector_hits_stale"
        else:
            fallback_reason = "vector_store_unavailable"
    except Exception as exc:
        if not fallback_enabled:
            raise
        fallback_reason = f"vector_store_error:{exc.__class__.__name__}"

    if not fallback_enabled:
        return VectorSearchResult(
            matches=[],
            source="NONE",
            fallback_reason=fallback_reason,
            vector_hit_count=vector_hit_count,
        )

    matches = _build_fallback_matches(
        model=model,
        description=description,
        description_id=description_id,
        photo_list=photo_list,
        photo_id_list=photo_id_list,
        top_k=top_k,
        predict_fn=predict_fn,
    )
    return VectorSearchResult(
        matches=matches,
        source="BRUTE_FORCE",
        fallback_reason=fallback_reason,
        vector_hit_count=vector_hit_count,
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
    result = build_text_search_result_with_vector_store(
        vector_store=vector_store,
        model=model,
        user_id=user_id,
        description=description,
        description_id=description_id,
        photo_list=photo_list,
        photo_id_list=photo_id_list,
        top_k=top_k,
        fallback_enabled=fallback_enabled,
        encode_texts_fn=encode_texts_fn,
        predict_fn=predict_fn,
    )
    return result.matches
