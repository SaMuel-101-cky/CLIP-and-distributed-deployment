# Embedding Backfill Design

## Goal

Add an explicit embedding backfill flow so the project can rebuild Chroma image vectors from MySQL photo metadata and local image files. This makes the vector index recoverable after local deletion, server migration, or adding Chroma after photos already exist.

## Intent And Constraints

The current CLIP project has a working Java backend, Python model service, MySQL source of truth, and local Chroma vector search. The next step is to make vector data operationally maintainable instead of only being written opportunistically during category upload.

The design must preserve the current service boundary:

- Java backend owns MySQL, user APIs, photo metadata, AI task state, embedding metadata, and callback validation.
- Python model service owns CLIP inference, image embedding generation, Chroma upsert, and result callbacks.
- Python must not connect to or mutate MySQL directly.
- Chroma stores the actual vector payloads and lightweight metadata.
- MySQL `embedding_records` stores auditable vector metadata only.
- HTTP remains the transport. Do not introduce WebSocket transport.
- Redis is not required.
- Ignore `CLIP_frontend` for this phase.

## Recommended Approach

Implement backfill as a new AI task type using the existing `EMBEDDING_BACKFILL` value in `ai_tasks.task_type`.

The backend exposes a user-facing endpoint that creates an `EMBEDDING_BACKFILL` task for active photos, then calls a new Python endpoint. The Python service encodes those images, upserts them into Chroma using the existing vector id format, and calls a new backend callback endpoint with embedding metadata. The backend validates that callback against the task-photo join table, upserts `embedding_records`, and marks the task `SUCCESS` or `FAILED`.

This keeps MySQL authoritative and lets Chroma be deleted and rebuilt without losing durable application state.

## Data Ownership

MySQL remains the source of truth for:

- user identity;
- photo metadata and photo status;
- AI task lifecycle;
- which photos were included in a backfill task;
- embedding metadata in `embedding_records`.

Chroma remains the source for:

- actual image embedding vectors;
- vector metadata needed for local query filtering and debugging.

The system must tolerate Chroma data loss. If `CLIP_model/chroma_data` or another configured Chroma persistence directory is deleted, calling backfill should recreate image vectors from active MySQL photo rows and local files.

## API Contracts

### User Backfill Request

Add a backend endpoint:

```text
POST /user/embeddings/backfill
```

Request body:

```json
{
  "username": "alice",
  "photoIds": [1, 2, 3]
}
```

Rules:

- `username` is required, following the current backend user API style.
- `photoIds` is optional.
- If `photoIds` is omitted or empty, backfill all active photos for that user.
- If `photoIds` is present, every id must belong to the user and have `status='ACTIVE'`.
- If no active photos are available, fail the request with a clear message and do not call Python.

Success response should use the existing `Result.success(...)` wrapper with at least:

```json
{
  "taskId": 123,
  "photoCount": 10,
  "msg": "Embedding backfill task accepted"
}
```

### Backend To Model Request

Add a Python endpoint:

```text
POST /embeddings/backfill
```

Backend request payload:

```json
{
  "taskId": 123,
  "userId": 7,
  "photosId": [1, 2, 3],
  "photosList": [
    "D:/CLIP/CLIP_backend/Management/photo_resources/a.png"
  ],
  "embeddingModel": "clip-vit-l-14",
  "vectorDb": "chroma",
  "collectionName": "clip_image_embeddings"
}
```

Rules:

- `taskId`, `userId`, `photosId`, and `photosList` are required.
- `photosId` and `photosList` must have the same nonzero length.
- `embeddingModel`, `vectorDb`, and `collectionName` may be included by the backend for explicitness. Python should still use its runtime config as the operational source for Chroma.
- Python should reject or fail the task if vector store is disabled or unavailable, because this endpoint exists specifically to create real vectors.
- `CLIP_SMOKE_MODE=true` should not create fake `embedding_records`. It may return an immediate failed task result with a clear message such as `Embedding backfill requires real CLIP mode`.

### Model To Backend Callback

Add a backend callback endpoint:

```text
POST /ai/tasks/{taskId}/embeddings
```

Callback payload:

```json
{
  "taskId": 123,
  "status": "SUCCESS",
  "errorMessage": null,
  "records": [
    {
      "photoId": 1,
      "targetType": "PHOTO",
      "embeddingModel": "clip-vit-l-14",
      "vectorDb": "chroma",
      "collectionName": "clip_image_embeddings",
      "vectorId": "photo:1:clip-vit-l-14",
      "dim": 768,
      "status": "READY"
    }
  ]
}
```

Failure callback:

```json
{
  "taskId": 123,
  "status": "FAILED",
  "errorMessage": "unable to read image: ...",
  "records": []
}
```

Rules:

- Reuse `X-AI-Callback-Token` validation from the existing match callback.
- The callback path `taskId` must match the request body `taskId` when the body has one.
- The backend must verify that the task exists and has `task_type='EMBEDDING_BACKFILL'`.
- Every callback record must point to a photo in `ai_task_photos` for that task.
- Every callback record in the first version should use `targetType='PHOTO'`.
- On `SUCCESS`, `records` must not be empty.
- On `SUCCESS`, upsert all records into `embedding_records`, then mark the task `SUCCESS`.
- On `FAILED`, mark the task `FAILED` with the callback error and do not require records.

## Embedding Record Semantics

Use the existing `embedding_records` schema without requiring a migration in this phase.

Field mapping:

| Column | Value |
|---|---|
| `user_id` | owning user id from the AI task |
| `target_type` | `PHOTO` |
| `target_id` | photo id |
| `embedding_model` | `clip-vit-l-14` by default |
| `vector_db` | `chroma` |
| `collection_name` | `clip_image_embeddings` by default |
| `vector_id` | `photo:{photo_id}:{embedding_model}` |
| `dim` | embedding vector dimension returned by Python |
| `status` | `READY` |

The table already has `UNIQUE KEY uk_embedding_target_model (target_type, target_id, embedding_model)`. Upsert should update vector metadata and `status` for reruns. This makes backfill idempotent.

If a photo is later soft-deleted, the first implementation does not need to delete its Chroma vector or mark the embedding record stale. Text search already filters by active backend photos and ignores stale vector hits not in the backend payload. A later cleanup task can mark or delete stale vectors.

## Python Model Behavior

The model service should add focused helpers rather than expanding `client.py` deeply:

- validate backfill request payloads;
- encode images with `encode_images(model, photosList)`;
- upsert to Chroma with `ChromaVectorStore.upsert_image_embeddings(...)`;
- build embedding records from `photoIds`, `embedding_model`, collection config, and embedding dimension;
- callback to the backend with `SUCCESS` or `FAILED`.

The existing `build_photo_vector_id(photo_id, embedding_model)` must remain the canonical vector id helper.

Backfill should log:

- task id;
- user id;
- number of photos requested;
- Chroma collection name;
- embedding dimension;
- success or failure reason.

## Backend Behavior

The backend should add a small `EmbeddingBackfillService` rather than mixing the flow into match or category services.

Responsibilities:

1. Resolve and validate the user.
2. Select active photo records.
3. Create an `EMBEDDING_BACKFILL` task with `PENDING` status.
4. Insert task-photo join rows.
5. POST the payload to `/embeddings/backfill`.
6. Mark the task `RUNNING` after the model accepts the request.
7. If the model request fails immediately, mark the task `FAILED`.

The callback service should:

1. Validate token through the controller.
2. Load the task by id.
3. Validate task type.
4. Validate record photo membership.
5. Upsert `embedding_records`.
6. Mark the task state.

## Error Handling

- No active photos: user request fails before creating or calling a model task.
- Some requested photo ids are missing, deleted, or owned by another user: user request fails before creating or calling a model task.
- Python vector store disabled or unavailable: Python callbacks `FAILED`.
- Python cannot read any image: Python callbacks `FAILED`.
- Python succeeds in Chroma but backend rejects callback: Python logs the callback failure; rerunning backfill must be safe because Chroma upsert and MySQL upsert are idempotent.
- Duplicate backfill requests are allowed. New AI tasks are created, and `embedding_records` are updated in place by target/model uniqueness.

## Testing Requirements

Backend unit tests:

- selecting all active photos when `photoIds` is omitted;
- rejecting requested photo ids that do not all belong to the user as active photos;
- creating an `EMBEDDING_BACKFILL` task and task-photo joins;
- posting the exact model payload to `/embeddings/backfill`;
- upserting embedding callback records and marking the task `SUCCESS`;
- marking the task `FAILED` when a failure callback arrives;
- rejecting embedding callbacks for non-`EMBEDDING_BACKFILL` tasks or photos outside the task.

Model unit tests:

- backfill payload validation rejects missing fields and mismatched list lengths;
- record builder uses `photo:{photo_id}:clip-vit-l-14` ids and the actual embedding dimension;
- processor upserts image embeddings and returns callback records;
- vector store unavailable produces a failed callback payload;
- `CLIP_SMOKE_MODE=true` path does not create fake successful embedding records.

Manual smoke tests:

- delete or move local `CLIP_model/chroma_data`;
- start backend and model in real GPU mode;
- call `/user/embeddings/backfill` for a user with active photos;
- confirm Chroma collection count increases;
- confirm `embedding_records` rows are `READY`;
- run text search and confirm model logs `文本搜图命中 Chroma`.

## Out Of Scope

- Milvus support.
- Cloud object storage.
- Redis, queues, or scheduled workers.
- Frontend UI for triggering backfill.
- Automatic backfill scheduling.
- Deleting Chroma vectors when photos are soft-deleted.
- Description/text embedding persistence.
- Multi-model embedding version migration beyond storing `embedding_model`.

## Operational Notes

The first version is a manual rebuild command through the backend API. That is enough for local learning, server migration testing, and interview explanation. Later production hardening can add a scheduled job, admin-only endpoint, progress counters, and cleanup for stale vectors.
