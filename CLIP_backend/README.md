# CLIP Backend

Spring Boot backend for the CLIP photo retrieval project. The backend owns MySQL state, local file metadata, user APIs, and AI task/result persistence.

## Current Architecture

```text
Client
  -> Spring Boot API
  -> MySQL: users, photos, descriptions, ai_tasks, matches
  -> Local disk storage through FileStorageService
  -> Python CLIP service over HTTP
  <- Python callback to /ai/tasks/{taskId}/matches
```

The Python model service must not write business tables directly. It receives photo/query payloads from Java and posts structured match results back to Java.

## Modules

```text
CLIP_backend/
├── common/       shared utilities
├── demo-pojo/    entities, DTOs, query objects, VO classes
├── Management/   Spring Boot application
└── schema.sql    canonical local MySQL schema
```

## Database

The canonical schema is:

```text
CLIP_backend/schema.sql
```

Main tables:

| Table | Purpose |
|---|---|
| `users` | User accounts |
| `photos` | Image metadata, storage paths, access URLs, soft-delete fields |
| `descriptions` | Category labels, search queries, and descriptions |
| `ai_tasks` | AI task lifecycle: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED` |
| `ai_task_photos` | Task-photo membership |
| `ai_task_descriptions` | Task-description membership |
| `photo_description_matches` | Ranked AI match results with score and match type |
| `embedding_records` | Metadata for future vector database records |

Import/reset locally:

```powershell
cd D:\CLIP\CLIP_backend
& 'C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe' -u root -p < schema.sql
```

## Configuration

Backend environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `MYSQL_HOST` | `127.0.0.1` | MySQL host |
| `MYSQL_PASSWORD` | empty | MySQL root password in the local profile |
| `CLIP_UPLOAD_DIR` | `photo_resources` | Local upload directory |
| `AI_SERVICE_BASE_URL` | `http://localhost:5000` | Python model service URL |
| `AI_CALLBACK_TOKEN` | empty | Optional callback shared token |

Development storage defaults to:

```text
CLIP_backend/Management/photo_resources
```

Storage goes through `FileStorageService`, so production can later replace local disk with object storage without changing controllers.

## Build And Run

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_HOST='127.0.0.1'
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package

cd D:\CLIP\CLIP_backend\Management
$env:AI_SERVICE_BASE_URL='http://localhost:5000'
java -jar .\target\Management-0.0.1-SNAPSHOT.jar
```

Default backend URL:

```text
http://localhost:8080
```

## Important APIs

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/user/register` | Register user |
| `POST` | `/user/login` | Login and return JWT |
| `POST` | `/user/category/upload` | Upload photos and category labels, then create a `CATEGORY` AI task |
| `GET` | `/user/category/download?username=<name>&taskId=<id>` | Read category result URLs |
| `POST` | `/user/match/upload` | Submit a text search query and create a `TEXT_SEARCH` AI task |
| `GET` | `/user/match/download?username=<name>&taskId=<id>` | Read text-search result URLs |
| `GET` | `/user/photos/list` | List active photos |
| `GET` | `/user/photos/binlist` | List soft-deleted photos |
| `DELETE` | `/user/photos/{url}` | Soft-delete a photo |
| `DELETE` | `/user/photos/bin/{url}` | Permanently delete from recycle bin |
| `POST` | `/ai/tasks/{taskId}/matches` | Model callback for AI task results |

## Notes

- Redis is not required in the current backend. If it is reintroduced, keep MySQL as the durable source of truth.
- Use `taskId` in API contracts and code. Do not reintroduce the old `idNum` batch identifier.
- Use HTTP between backend and model service. Do not add WebSocket transport unless explicitly requested.
- See `D:\CLIP\docs\local-runbook.md` for local startup, GPU checks, and troubleshooting.
