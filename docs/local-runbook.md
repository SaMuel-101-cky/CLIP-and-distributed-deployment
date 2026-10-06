# Local Runbook

This runbook captures the current backend-first CLIP refactor state. The Java backend owns MySQL and file metadata; the Python model service only performs inference and posts results back to the backend.

For a ready-to-import request collection and the recommended manual flow, see [Apifox 后端联调指南](apifox-debugging.md).

## AI task observability and benchmark procedure

Authenticated users can inspect one task with `GET /user/ai-tasks/{taskId}` and page through their own embedding records with `GET /user/embeddings?status=READY&embeddingModel=clip-vit-l-14&page=1&pageSize=20`. Both derive ownership from the JWT; do not supply another user's ID or username.

The Java Prometheus endpoint is `GET /actuator/prometheus` and remains Spring-Security protected. The model endpoint is `GET /metrics`; set `METRICS_ENABLED=true` and a nonempty `METRICS_TOKEN`, then send it in `X-Metrics-Token`. Bind both endpoints privately or protect them with a reverse-proxy/network allowlist: they are internal monitoring endpoints, not public client APIs.

Metrics are `clip_ai_task_transitions_total{task_type,status}`, `clip_ai_task_terminal_duration_seconds{task_type,status}`, `clip_embedding_records_upserted_total{embedding_model,vector_db,status}`, `clip_embedding_backfill_jobs_total{outcome}`, `clip_embedding_backfill_duration_seconds{outcome}`, `clip_embedding_backfill_photos_total{outcome}`, and `clip_embedding_callback_failures_total{outcome}`. Labels are bounded; task/user/photo IDs, queries, paths, and exception text are never labels.

For a reproducible real-mode measurement: record CPU/RAM/disk and GPU state with `nvidia-smi`; disable smoke mode; submit a known image count; poll the task API to a terminal status; scrape both protected exporters; and retain the task ID with matching lifecycle logs. Throughput = successful images / elapsed seconds. Failure rate = failed tasks / terminal tasks. Derive p95 from Prometheus histogram quantiles. GPU utilization is not exported by this implementation.

## Prerequisites

- JDK 23
- Maven 3.8+
- MySQL 8/9 with a local `root` account
- Python environment: `D:\Anaconda\envs\CLIP\python.exe`
- CUDA PyTorch in the CLIP environment for GPU inference
- CLIP weights available to the model service

## Database

Canonical schema:

```text
D:\CLIP\CLIP_backend\schema.sql
```

Import/reset the local schema:

```powershell
cd D:\CLIP\CLIP_backend
& 'C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe' -u root -p < schema.sql
```

Main tables:

- `users`
- `photos`
- `descriptions`
- `ai_tasks`
- `ai_task_photos`
- `ai_task_descriptions`
- `photo_description_matches`
- `embedding_records`

The model service should not connect to MySQL directly. Java persists task state and match results.

`embedding_records` holds auditable Chroma vector metadata, not vector payloads. Chroma persistence is rebuildable: use embedding backfill to repopulate it from active photo metadata and local files.

## Backend Startup

Build and test:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_HOST='127.0.0.1'
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package
```

Run:

```powershell
cd D:\CLIP\CLIP_backend\Management
$env:MYSQL_HOST='127.0.0.1'
$env:MYSQL_PASSWORD='<local mysql password>'
$env:AI_SERVICE_BASE_URL='http://localhost:5000'
java -jar .\target\Management-0.0.1-SNAPSHOT.jar
```

Useful backend checks:

```powershell
Test-NetConnection -ComputerName localhost -Port 8080
```

Development uploads default to:

```text
D:\CLIP\CLIP_backend\Management\photo_resources
```

Override with:

```powershell
$env:CLIP_UPLOAD_DIR='D:\some\other\upload\dir'
```

## Model Startup

Real CLIP/GPU mode:

```powershell
cd D:\CLIP\CLIP_model
$env:BACKEND_BASE_URL='http://localhost:8080'
Remove-Item Env:\CLIP_SMOKE_MODE -ErrorAction SilentlyContinue
D:\Anaconda\envs\CLIP\python.exe client.py
```

Smoke mode for fast backend/callback/database testing:

```powershell
cd D:\CLIP\CLIP_model
$env:BACKEND_BASE_URL='http://localhost:8080'
$env:CLIP_SMOKE_MODE='true'
D:\Anaconda\envs\CLIP\python.exe client.py
```

Health check:

```powershell
Invoke-RestMethod http://localhost:5000/health
```

Expected GPU signal on this machine:

```json
{"status":"healthy","device":"cuda:0"}
```

If `/health` reports CPU, verify the interpreter:

```powershell
D:\Anaconda\envs\CLIP\python.exe -c "import torch; print(torch.__version__); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'no cuda')"
```

## Backend-Model Flow

Category upload:

1. Client calls `POST /user/category/upload`.
2. Backend stores uploaded files through `FileStorageService`.
3. Backend inserts `photos`, `descriptions`, `ai_tasks`, and join-table rows.
4. Backend calls model `POST /upload`.
5. Model computes matches.
6. Model posts results to `POST /ai/tasks/{taskId}/matches`.
7. Backend persists `photo_description_matches` and marks the task `SUCCESS` or `FAILED`.

Text search:

1. Client calls `POST /user/match/upload`.
2. Backend inserts a `SEARCH_QUERY` description and a `TEXT_SEARCH` task.
3. Backend sends active user photos and the query to model `POST /getPhotos`.
4. Model posts ranked results to `POST /ai/tasks/{taskId}/matches`.
5. Client reads result URLs through `GET /user/match/download?username=<name>&taskId=<id>`.

Embedding backfill:

1. Call `POST /user/embeddings/backfill` with `username` and optional `photoIds`.
2. Java creates an `EMBEDDING_BACKFILL` task and posts active local paths to `POST /embeddings/backfill`.
3. Python upserts real CLIP vectors into Chroma and calls `POST /ai/tasks/{taskId}/embeddings`.
4. Java validates task-photo membership and upserts `embedding_records` metadata.

`CLIP_SMOKE_MODE=true` returns a failed embedding callback; it never writes fake successful embedding records.

## Environment Variables

Backend:

| Variable | Default | Purpose |
|---|---|---|
| `MYSQL_HOST` | `127.0.0.1` | MySQL host |
| `MYSQL_PASSWORD` | empty | MySQL password for the local Spring profile |
| `CLIP_UPLOAD_DIR` | `photo_resources` | Local upload directory |
| `AI_SERVICE_BASE_URL` | `http://localhost:5000` | Python model service URL |
| `AI_CALLBACK_TOKEN` | empty | Optional callback shared token |

Model:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `5000` | Flask port |
| `BACKEND_BASE_URL` | `http://localhost:8080` | Java callback URL |
| `AI_CALLBACK_TOKEN` | unset | Must match backend when configured |
| `TEXT_SEARCH_TOP_K` | `5` | Text search TopK |
| `CLIP_SMOKE_MODE` | `false` | Skip heavy model inference for smoke tests |
| `VECTOR_STORE_ENABLED` | `true` | Enable Chroma image-vector upsert and text search |
| `CHROMA_PERSIST_DIR` | `./chroma_data` | Local Chroma persistence directory |
| `CHROMA_COLLECTION` | `clip_image_embeddings` | Chroma image embedding collection |
| `EMBEDDING_MODEL_NAME` | `clip-vit-l-14` | Vector id and metadata namespace |
| `VECTOR_SEARCH_FALLBACK` | `true` | Fall back to brute-force CLIP scoring when Chroma is unavailable or empty |

Chroma data is rebuildable local index state. If `CHROMA_PERSIST_DIR` is deleted, upload/category tasks can regenerate image vectors from MySQL photo metadata and local image files.

## Verification Commands

Model tests:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\server.py CLIP_model\utils\config.py CLIP_model\utils\pred.py CLIP_model\utils\embedding.py CLIP_model\manager\ai_task_payload.py CLIP_model\manager\backend_client.py CLIP_model\manager\vector_store.py CLIP_model\manager\vector_search.py
```

Backend tests:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
```

Backend package:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q -DskipTests package
```

## Troubleshooting

- Maven fails with database connection errors: confirm `MYSQL_HOST`, `MYSQL_PASSWORD`, and that `photo_system` was initialized from `schema.sql`.
- `/health` shows CPU: the wrong Python environment or CPU-only PyTorch is being used.
- Chroma import or startup fails: keep `VECTOR_SEARCH_FALLBACK=true` so text search can use brute-force CLIP scoring while the local Chroma dependency is repaired.
- Chroma returns no results: confirm at least one non-smoke upload/category task has run for that user so image vectors exist.
- AI task remains `RUNNING`: the model process likely crashed or was stopped before callback. Inspect model logs, then mark/retry the task intentionally.
- Uploaded images are inaccessible under `/images/...`: check `CLIP_UPLOAD_DIR`, `file.access-path`, and whether the backend was started from `CLIP_backend/Management`.
- Do not use Redis as the only recycle-bin state. Soft-delete fields in MySQL are the durable source of truth.
