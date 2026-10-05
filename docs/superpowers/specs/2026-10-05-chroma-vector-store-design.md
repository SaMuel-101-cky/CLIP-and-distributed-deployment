# Chroma Vector Store Design

## Goal

Add Chroma as the first local vector database for the CLIP project so uploaded image embeddings can be reused for later text-to-image searches instead of recomputing every image on every query.

## Intent And Constraints

The current priority is a local, backend-first refactor that can be run and tested on the user's machine. Chroma is chosen because it is lightweight and easier to operate locally than Milvus. The design must preserve the current service boundary:

- Java backend owns MySQL, user-facing APIs, photo metadata, AI task state, and persisted match history.
- Python model service owns CLIP inference, embedding generation, and vector search.
- Chroma stores reusable vectors and lightweight search metadata.
- HTTP remains the service transport. Do not introduce WebSocket transport.
- Redis is not required for this phase.
- Local photo files remain under `CLIP_backend/Management/photo_resources` unless `CLIP_UPLOAD_DIR` overrides that path.

## Recommended Approach

Implement Chroma inside `CLIP_model` behind a small vector-store module. The Java backend should continue calling the model service over the existing `/upload` and `/getPhotos` endpoints.

The first implementation should support:

1. Image embedding upsert during category upload.
2. Chroma-backed text-to-image search.
3. Fallback to current brute-force CLIP scoring if Chroma is unavailable or has no matching records.
4. Optional future callback metadata so Java can write `embedding_records`, but do not make this required for the first local demo.

This keeps the integration small while proving the important product behavior: once images have been vectorized, repeated text queries can search existing image vectors quickly.

## Data Ownership

MySQL remains the source of truth for:

- `users`
- `photos`
- `descriptions`
- `ai_tasks`
- `ai_task_photos`
- `ai_task_descriptions`
- `photo_description_matches`
- `embedding_records`

Chroma stores:

- vector id, preferably `photo:{photo_id}:clip-vit-l-14`;
- image embedding vector;
- metadata: `photo_id`, `user_id`, `storage_path`, `embedding_model`, and `created_at`;
- optional document text such as the stored file name or path.

Chroma must not be the only place where a photo exists. If a Chroma record is missing, the system should be able to rebuild it from MySQL photo metadata and local image files.

## Collection Design

Use one Chroma collection for image embeddings:

```text
clip_image_embeddings
```

Default vector id format:

```text
photo:{photo_id}:clip-vit-l-14
```

Recommended metadata:

| Field | Type | Purpose |
|---|---|---|
| `photo_id` | int | Maps search result back to MySQL |
| `user_id` | int | Enforces user-level isolation |
| `storage_path` | string | Debugging and rebuild support |
| `embedding_model` | string | Prevents mixing incompatible vectors |
| `created_at` | string | Operational visibility |

The first version should filter by `user_id` during query. If local Chroma filtering becomes limiting later, use per-user collections or move to Milvus.

## Model-Side Components

Create a focused vector-store layer:

```text
CLIP_model/
├── manager/
│   └── vector_store.py
└── utils/
    └── embedding.py
```

`utils/embedding.py` should expose CLIP embedding helpers:

- `encode_images(model, image_paths) -> numpy.ndarray`
- `encode_texts(model, texts) -> numpy.ndarray`

Both functions should:

- reuse the active model device;
- normalize vectors before storage/search;
- share image preprocessing with the existing `predict()` path where practical.

`manager/vector_store.py` should expose a small interface:

- `VectorStoreConfig`
- `ChromaVectorStore`
- `upsert_image_embeddings(user_id, photo_ids, image_paths, embeddings)`
- `query_images(user_id, query_embedding, top_k)`
- `has_user_images(user_id)`

The rest of the model service should not import Chroma directly.

## API/Data Flow

### Category Upload

1. Java receives `POST /user/category/upload`.
2. Java stores files and writes MySQL records.
3. Java calls model `POST /upload` with `taskId`, `photosId`, `photosList`, `descriptionsId`, and `descriptionsList`.
4. Model encodes uploaded images.
5. Model upserts image embeddings to Chroma.
6. Model computes category matches against the provided category labels.
7. Model callbacks to Java `POST /ai/tasks/{taskId}/matches`.
8. Java persists `photo_description_matches` and marks the task `SUCCESS` or `FAILED`.

### Text Search

1. Java receives `POST /user/match/upload`.
2. Java writes a `SEARCH_QUERY` description and a `TEXT_SEARCH` task.
3. Java calls model `POST /getPhotos` with `taskId`, `userId`, `description`, `descriptionId`, `photosId`, and `photosList`.
4. Model encodes the query text.
5. Model queries Chroma for image embeddings where `user_id` matches.
6. Model converts Chroma hits into `photoId`, `descriptionId`, `matchType`, `score`, and `rankNo`.
7. Model callbacks to Java.
8. Java persists result rows and serves URLs from MySQL/photo storage.

### Fallback

If Chroma is disabled, unavailable, or returns no records for the user, `/getPhotos` should use the current brute-force path:

```text
predict(model, photosList, [description])
```

The fallback keeps local demos resilient while Chroma is being introduced.

## Configuration

Model environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `VECTOR_STORE_ENABLED` | `true` | Enable Chroma search and upsert |
| `CHROMA_PERSIST_DIR` | `./chroma_data` | Local Chroma persistence directory |
| `CHROMA_COLLECTION` | `clip_image_embeddings` | Image embedding collection |
| `EMBEDDING_MODEL_NAME` | `clip-vit-l-14` | Metadata and vector id namespace |
| `VECTOR_SEARCH_FALLBACK` | `true` | Fall back to brute-force CLIP scoring |

Backend environment does not need new required variables in the first version.

## Error Handling

- If Chroma upsert fails during `/upload`, the category task should still be allowed to complete if CLIP classification succeeds. Log the vector-store error clearly.
- If Chroma query fails during `/getPhotos` and fallback is enabled, use brute-force scoring and mark the task `SUCCESS` if callback succeeds.
- If both Chroma and fallback fail, callback `FAILED` with a clear error message.
- If Chroma returns a `photo_id` that was not included in the backend payload, ignore it. This guards against stale vectors.
- If fewer than `top_k` results are available, return the available results only.

## Testing Requirements

Model unit tests:

- vector id generation is deterministic;
- Chroma hit conversion preserves `photoId`, `descriptionId`, rank order, and score;
- query filters out stale photo ids not present in the backend payload;
- Chroma failure falls back to brute-force scoring when fallback is enabled;
- callback payload remains compatible with the Java `AiMatchResultDto`.

Backend tests:

- current Maven tests must continue passing;
- no backend Redis dependency is reintroduced;
- no model-side MySQL dependency is reintroduced.

Manual smoke tests:

- model `/health` reports `cuda:0` on the local CLIP environment;
- first text search after upload can build/use Chroma vectors;
- repeated text search avoids rescoring all images through brute force when Chroma has records;
- Java download endpoints return `/images/...` URLs from persisted match rows.

## Out Of Scope

- Milvus integration.
- Cloud object storage.
- Redis caching.
- Message queue based async orchestration.
- Frontend changes.
- WebSocket transport.
- Full production deployment hardening.

## Migration Notes

The current `embedding_records` table is ready to record vector metadata, but the first Chroma integration can run without writing it. A later backend enhancement can add a callback or endpoint for model-side embedding status:

```text
photo_id, target_type=PHOTO, embedding_model, vector_db=chroma, collection_name, vector_id, dim, status
```

That enhancement should be implemented only after the local Chroma search path is working.
