from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Iterable, List

import numpy as np


def build_photo_vector_id(photo_id: int, embedding_model: str) -> str:
    return f"photo:{int(photo_id)}:{embedding_model}"


@dataclass(frozen=True)
class VectorSearchHit:
    photo_id: int
    score: float
    rank_no: int


@dataclass(frozen=True)
class VectorStoreConfig:
    enabled: bool
    persist_dir: str
    collection_name: str
    embedding_model: str
    fallback_enabled: bool


def build_text_search_matches_from_hits(
    hits: Iterable[VectorSearchHit],
    description_id: int,
    allowed_photo_ids: List[int],
) -> list[dict]:
    allowed = {int(photo_id) for photo_id in allowed_photo_ids}
    matches = []

    for hit in sorted(hits, key=lambda item: item.rank_no):
        if int(hit.photo_id) not in allowed:
            continue
        matches.append({
            "photoId": int(hit.photo_id),
            "descriptionId": int(description_id),
            "matchType": "TEXT_SEARCH",
            "score": float(hit.score),
            "rankNo": len(matches) + 1,
        })

    return matches


class ChromaVectorStore:
    def __init__(self, config: VectorStoreConfig, client_factory=None):
        self.config = config
        self.collection = None
        if not config.enabled:
            return

        if client_factory is None:
            import chromadb

            client_factory = lambda persist_dir: chromadb.PersistentClient(path=persist_dir)

        self.client = client_factory(config.persist_dir)
        self.collection = self.client.get_or_create_collection(
            name=config.collection_name,
            metadata={"hnsw:space": "cosine"},
        )

    def upsert_image_embeddings(self, user_id, photo_ids, image_paths, embeddings) -> None:
        if not self.config.enabled or self.collection is None:
            return

        vectors = np.asarray(embeddings, dtype=np.float32)
        now = datetime.now(timezone.utc).isoformat()
        ids = [
            build_photo_vector_id(photo_id, self.config.embedding_model)
            for photo_id in photo_ids
        ]
        metadatas = [
            {
                "photo_id": int(photo_id),
                "user_id": int(user_id),
                "storage_path": str(image_path),
                "embedding_model": self.config.embedding_model,
                "created_at": now,
            }
            for photo_id, image_path in zip(photo_ids, image_paths)
        ]

        self.collection.upsert(
            ids=ids,
            embeddings=vectors.tolist(),
            metadatas=metadatas,
            documents=[str(image_path) for image_path in image_paths],
        )

    def query_images(self, user_id, query_embedding, top_k) -> list[VectorSearchHit]:
        if not self.config.enabled or self.collection is None:
            return []

        vector = np.asarray(query_embedding, dtype=np.float32)
        if vector.ndim == 1:
            query_embeddings = [vector.tolist()]
        else:
            query_embeddings = vector.tolist()

        result = self.collection.query(
            query_embeddings=query_embeddings,
            n_results=int(top_k),
            where={"user_id": int(user_id)},
            include=["metadatas", "distances"],
        )

        metadatas = (result.get("metadatas") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]
        hits = []
        for index, metadata in enumerate(metadatas):
            if not metadata or "photo_id" not in metadata:
                continue
            distance = float(distances[index]) if index < len(distances) else 1.0
            hits.append(VectorSearchHit(
                photo_id=int(metadata["photo_id"]),
                score=round(1.0 - distance, 10),
                rank_no=len(hits) + 1,
            ))

        return sorted(hits, key=lambda item: item.score, reverse=True)

    def has_user_images(self, user_id) -> bool:
        if not self.config.enabled or self.collection is None:
            return False

        result = self.collection.get(
            where={"user_id": int(user_id)},
            limit=1,
        )
        return bool(result.get("ids"))
