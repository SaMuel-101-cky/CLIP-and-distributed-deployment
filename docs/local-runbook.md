# Local Runbook

This runbook captures the current backend-first CLIP refactor state. The Java backend owns MySQL and file metadata; the Python model service only performs inference and posts results back to the backend.

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
