# AGENTS.md

## Project Direction

This repository is being refactored from a course-style CLIP image-text retrieval project into a cleaner backend + model service that can later be deployed and benchmarked.

Current priorities:

1. Keep the MySQL schema explicit and source-of-truth.
2. Keep the Java backend responsible for user state, file metadata, AI task state, and match result persistence.
3. Keep the Python CLIP service focused on inference and result callbacks.
4. Keep storage behind `FileStorageService` so local disk can later be replaced by cloud/object storage.
5. Add vector DB support only after the relational model and callback flow are stable.

Prefer HTTP/SSE transport for Codex and model-provider connections. Do not introduce or enable WebSocket transport unless explicitly requested and verified.

## Current Architecture

- `CLIP_backend`: Java Spring Boot backend with MyBatis, MySQL, JWT auth, local photo storage, AI task orchestration, and AI callback ingestion.
- `CLIP_model`: Python Flask service for CLIP inference and optional model-component offloading.
- `CLIP_frontend`: legacy frontend. Ignore it for the current backend-first refactor unless the user explicitly asks.

Ownership boundary:

- MySQL is owned by the Java backend.
- Python must not write Java-owned business tables directly.
- Python receives task payloads from Java and posts structured results back to `/ai/tasks/{taskId}/matches`.
- Redis is not currently required. If reintroduced, it should be cache/session/rate-limit/progress only, never the source of truth.

## Data Model

The canonical schema is `CLIP_backend/schema.sql`.

Core tables:

- `users`: account records.
- `photos`: uploaded image metadata, including `storage_path`, `access_url`, original/stored names, content hash, mime type, size, and soft-delete fields.
- `descriptions`: category labels, search queries, or general descriptions.
- `ai_tasks`: task state for `CATEGORY`, `TEXT_SEARCH`, and future `EMBEDDING_BACKFILL`.
- `ai_task_photos`: task-photo join table.
- `ai_task_descriptions`: task-description join table.
- `photo_description_matches`: AI match results with `score`, `rank_no`, and `match_type`.
- `embedding_records`: relational metadata for future vector DB entries.

Important rules:

- Do not reintroduce legacy `idNum`, `photos.descriptionId`, `description`, or `photo_description` concepts.
- Photo listing should query `photos` by `user_id` and `status`, independent of AI results.
- AI results belong in `photo_description_matches`, not in `photos`.
- `embedding_records` tracks vector metadata only. Actual vector payloads belong in a vector database.

## Runtime Configuration

Backend:

- `MYSQL_HOST`: MySQL host, default `127.0.0.1`.
- `MYSQL_PASSWORD`: MySQL root password for local profile.
- `CLIP_UPLOAD_DIR`: local upload directory, default `photo_resources` under `CLIP_backend/Management`.
- `AI_SERVICE_BASE_URL`: model service URL, default `http://localhost:5000`.
- `AI_CALLBACK_TOKEN`: optional shared token for model-to-backend callbacks.

Model:

- `SERVER_PORT`: Flask port, default `5000`.
- `BACKEND_BASE_URL`: Java backend URL for callbacks, default `http://localhost:8080`.
- `AI_CALLBACK_TOKEN`: must match backend if configured.
- `TEXT_SEARCH_TOP_K`: text search result count, default `5`.
- `CLIP_SMOKE_MODE`: `true` skips heavy CLIP inference and returns deterministic test matches.
- `VECTOR_STORE_ENABLED`: enables Chroma image-vector upsert/search, default `true`.
- `CHROMA_PERSIST_DIR`: local Chroma index directory, default `./chroma_data`.
- `CHROMA_COLLECTION`: Chroma image embedding collection, default `clip_image_embeddings`.
- `EMBEDDING_MODEL_NAME`: vector id/metadata namespace, default `clip-vit-l-14`.
- `VECTOR_SEARCH_FALLBACK`: fallback to brute-force CLIP scoring when Chroma is unavailable or empty, default `true`.

See `docs/local-runbook.md` for exact local startup and smoke test commands.

## Backend Rules

- Build from `CLIP_backend`.
- Keep the backend runnable without Redis.
- Keep file IO behind `FileStorageService`; local development uses `LocalFileStorageService`.
- Store development uploads in `CLIP_backend/Management/photo_resources` unless `CLIP_UPLOAD_DIR` overrides it.
- Keep `/images/**` static access aligned with `file.access-path`.
- Keep `/ai/tasks/**` available to the model callback. If `AI_CALLBACK_TOKEN` is configured, require the callback header.
- Use `taskId` in Java/Python contracts. Avoid the legacy name `idNum`.

Minimum backend verification:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package
```

## Model Rules

- Start the model from `CLIP_model`.
- Use `D:\Anaconda\envs\CLIP\python.exe` for local GPU debugging on this machine.
- `/health` should report `cuda:0` when CUDA PyTorch is available.
- Use `CLIP_SMOKE_MODE=true` only for backend/callback/database smoke tests.
- `CLIP_SMOKE_MODE=true` bypasses heavy inference and should not be used to validate Chroma behavior.
- Treat Chroma persistence as rebuildable index state; MySQL remains the source of truth.
- Keep model-backend communication over HTTP.
- Do not add direct MySQL access back into the model service.

Minimum model verification:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\server.py CLIP_model\utils\config.py CLIP_model\utils\pred.py CLIP_model\utils\embedding.py CLIP_model\manager\ai_task_payload.py CLIP_model\manager\backend_client.py CLIP_model\manager\vector_store.py CLIP_model\manager\vector_search.py
```

## Vector DB Guidance

Add a vector database only after backend and model smoke tests pass.

Recommended first implementation:

- Start with Chroma for local learning and simple demos.
- Keep the vector DB behind a small service/interface so Milvus can replace it later.
- Store image embeddings keyed by `photo_id`.
- Store optional text/query embeddings keyed by `description_id` or a query hash.
- Store `user_id`, `embedding_model`, and collection metadata with each vector record.
- Keep MySQL as the source of truth for users, photos, descriptions, tasks, and match history.

User isolation matters: vector searches must filter by `user_id` or use isolated collections.

## Engineering Rules

- Read existing code before editing.
- Keep backend and model contracts explicit with DTOs or typed payload helpers.
- Write schema/migration SQL whenever database shape changes.
- Run focused tests after each major refactor.
- If a build/test fails, diagnose root cause before applying fixes.
- Avoid frontend work until the user explicitly starts the new frontend phase.
