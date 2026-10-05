# Chroma Vector Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add local Chroma-backed image embedding reuse for CLIP text-to-image search while preserving Java backend ownership of MySQL and AI task state.

**Architecture:** Chroma lives inside `CLIP_model` behind `manager/vector_store.py`. The model service upserts image embeddings during `/upload`, queries Chroma during `/getPhotos`, and falls back to the current brute-force CLIP scoring path when Chroma is disabled, unavailable, or empty.

**Tech Stack:** Python 3.10+, Flask, PyTorch, NumPy, ChromaDB, Java Spring Boot/MyBatis unchanged for this phase.

**Spec:** `docs/superpowers/specs/2026-10-05-chroma-vector-store-design.md`

## Global Constraints

- Java backend remains the source of truth for MySQL, user APIs, photo metadata, AI task state, and match history.
- Python model service must not connect to or mutate MySQL directly.
- Chroma stores reusable vectors and lightweight metadata only.
- Keep backend/model transport over HTTP; do not introduce WebSocket transport.
- Redis is not required and must not be reintroduced.
- Local photo files remain under `CLIP_backend/Management/photo_resources` unless `CLIP_UPLOAD_DIR` overrides that path.
- First implementation must support fallback to brute-force CLIP scoring.
- Do not modify `CLIP_frontend`.

## Review Focus

- Chroma dependency missing or broken: `/getPhotos` should still use brute-force scoring when fallback is enabled.
- Stale Chroma records from another task/user: query results must be filtered by `user_id` and by the backend-supplied `photosId` set.
- Empty Chroma collection: text search should fall back instead of returning an empty successful task.
- Vector score direction: higher returned score should mean better match for backend ranking.
- Smoke mode: `CLIP_SMOKE_MODE=true` should keep bypassing heavy model inference and should not require Chroma.

---

## File Structure

- Modify `CLIP_model/utils/config.py`: add vector-store environment variables.
- Modify `CLIP_model/requirements.txt`: add Chroma dependency.
- Modify `CLIP_model/utils/pred.py`: extract reusable image preprocessing without changing `predict()` behavior.
- Create `CLIP_model/utils/embedding.py`: encode normalized image/text embeddings.
- Create `CLIP_model/manager/vector_store.py`: Chroma wrapper and result conversion helpers.
- Create `CLIP_model/manager/vector_search.py`: vector-first text search orchestration with brute-force fallback.
- Modify `CLIP_model/client.py`: initialize vector store, upsert image vectors in `/upload`, and query Chroma in `/getPhotos`.
- Create `CLIP_model/tests/test_embedding.py`: unit tests around preprocessing/normalization helpers using fake models where possible.
- Create `CLIP_model/tests/test_vector_store.py`: unit tests for ids, hit conversion, filtering, and fallback decision helpers.
- Modify `CLIP_model/README.md`, `CLIP_model/CLAUDE.md`, and `docs/local-runbook.md`: document Chroma config and smoke tests.

Java code changes are limited to adding `userId` to the category upload payload so the model can store Chroma vectors with user isolation.

## Task 1: Configuration And Dependency Setup

**Files:**
- Modify: `CLIP_model/utils/config.py`
- Modify: `CLIP_model/requirements.txt`
- Modify: `CLIP_model/tests/test_ai_task_payload.py` if helper imports need adjustment

**Interfaces:**
- Produces config constants:
  - `VECTOR_STORE_ENABLED: bool`
  - `CHROMA_PERSIST_DIR: str`
  - `CHROMA_COLLECTION: str`
  - `EMBEDDING_MODEL_NAME: str`
  - `VECTOR_SEARCH_FALLBACK: bool`

- [ ] **Step 1: Write the failing config test**

Add `CLIP_model/tests/test_config.py` with a `unittest.TestCase`:

```python
class ConfigTest(unittest.TestCase):
    def test_vector_store_defaults_are_local_chroma_values(self):
        from utils import config

        self.assertTrue(config.VECTOR_STORE_ENABLED)
        self.assertEqual("./chroma_data", config.CHROMA_PERSIST_DIR)
        self.assertEqual("clip_image_embeddings", config.CHROMA_COLLECTION)
        self.assertEqual("clip-vit-l-14", config.EMBEDDING_MODEL_NAME)
        self.assertTrue(config.VECTOR_SEARCH_FALLBACK)
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_config
```

Expected: FAIL because the constants are not defined.

- [ ] **Step 3: Add config constants**

In `CLIP_model/utils/config.py`, add:

```python
VECTOR_STORE_ENABLED = os.getenv("VECTOR_STORE_ENABLED", "true").lower() == "true"
CHROMA_PERSIST_DIR = os.getenv("CHROMA_PERSIST_DIR", "./chroma_data")
CHROMA_COLLECTION = os.getenv("CHROMA_COLLECTION", "clip_image_embeddings")
EMBEDDING_MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "clip-vit-l-14")
VECTOR_SEARCH_FALLBACK = os.getenv("VECTOR_SEARCH_FALLBACK", "true").lower() == "true"
```

- [ ] **Step 4: Add Chroma dependency**

Add `chromadb` to `CLIP_model/requirements.txt`. If the existing file contains platform-specific conda paths, preserve the file format and add a plain pip requirement line such as:

```text
chromadb>=0.5,<1.0
```

- [ ] **Step 5: Run the focused test and compile check**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_config
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\utils\config.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add CLIP_model\utils\config.py CLIP_model\requirements.txt CLIP_model\tests\test_config.py
git commit -m "feat: add vector store configuration"
```

## Task 2: Extract CLIP Embedding Helpers

**Files:**
- Modify: `CLIP_model/utils/pred.py`
- Create: `CLIP_model/utils/embedding.py`
- Create: `CLIP_model/tests/test_embedding.py`

**Interfaces:**
- Consumes: `utils.pred.get_model_device(model)` and `utils.pred.read_image_from_path(path)`.
- Produces:
  - `preprocess_images(image_paths: list[str], device: torch.device) -> torch.Tensor`
  - `encode_images(model, image_paths: list[str]) -> np.ndarray`
  - `encode_texts(model, texts: list[str]) -> np.ndarray`
  - `normalize_features(features: torch.Tensor) -> torch.Tensor`

- [ ] **Step 1: Write failing tests for normalization and fake-model encoding**

Create `CLIP_model/tests/test_embedding.py` with tests that:

- call `normalize_features(torch.tensor([[3.0, 4.0]]))` and assert the L2 norm is `1.0`;
- use a fake model with `encode_image()` returning `[[3.0, 4.0]]` and assert `encode_images()` returns normalized NumPy output;
- use a fake model with `encode_text()` returning `[[0.0, 2.0]]` and assert `encode_texts()` returns normalized NumPy output.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding
```

Expected: FAIL because `utils.embedding` does not exist.

- [ ] **Step 3: Refactor image preprocessing**

In `CLIP_model/utils/pred.py`, extract existing image loading/resizing/tensor stacking into:

```python
def preprocess_images(image_paths: List[str], device: torch.device):
    ...
```

Update `predict()` to call `preprocess_images()` and keep its existing return values unchanged.

- [ ] **Step 4: Implement embedding helpers**

Create `CLIP_model/utils/embedding.py` with:

```python
def normalize_features(features):
    ...

def encode_images(model, image_paths):
    ...

def encode_texts(model, texts):
    ...
```

`encode_texts()` should tokenize prompts as `a photo of a {text}` to match current `predict()` behavior.

- [ ] **Step 5: Run focused tests and existing model tests**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\utils\pred.py CLIP_model\utils\embedding.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add CLIP_model\utils\pred.py CLIP_model\utils\embedding.py CLIP_model\tests\test_embedding.py
git commit -m "feat: extract clip embedding helpers"
```

## Task 3: Add Chroma Vector Store Wrapper

**Files:**
- Create: `CLIP_model/manager/vector_store.py`
- Create: `CLIP_model/tests/test_vector_store.py`

**Interfaces:**
- Consumes config constants from Task 1.
- Produces:
  - `build_photo_vector_id(photo_id: int, embedding_model: str) -> str`
  - `VectorSearchHit(photo_id: int, score: float, rank_no: int)`
  - `build_text_search_matches_from_hits(hits, description_id: int, allowed_photo_ids: list[int]) -> list[dict]`
  - `VectorStoreConfig(enabled: bool, persist_dir: str, collection_name: str, embedding_model: str, fallback_enabled: bool)`
  - `ChromaVectorStore.upsert_image_embeddings(user_id, photo_ids, image_paths, embeddings) -> None`
  - `ChromaVectorStore.query_images(user_id, query_embedding, top_k) -> list[VectorSearchHit]`
  - `ChromaVectorStore.has_user_images(user_id) -> bool`

- [ ] **Step 1: Write failing tests for pure helpers**

In `CLIP_model/tests/test_vector_store.py`, add tests:

- `build_photo_vector_id(12, "clip-vit-l-14") == "photo:12:clip-vit-l-14"`;
- `build_text_search_matches_from_hits()` preserves descending rank order;
- stale hit with `photo_id` not in `allowed_photo_ids` is ignored;
- fewer valid hits than `top_k` returns only valid hits.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_vector_store
```

Expected: FAIL because `manager.vector_store` does not exist.

- [ ] **Step 3: Implement pure helper types/functions**

Create `CLIP_model/manager/vector_store.py` with helper functions and dataclasses. Keep imports of `chromadb` lazy inside `ChromaVectorStore.__init__()` so unit tests for pure helpers do not require Chroma to be installed.

- [ ] **Step 4: Implement Chroma wrapper**

In `ChromaVectorStore`:

- create a persistent Chroma client at `persist_dir`;
- get or create `collection_name` with cosine distance metadata, for example `metadata={"hnsw:space": "cosine"}`;
- upsert ids, embeddings, documents, and metadata;
- query with `where={"user_id": int(user_id)}`;
- convert Chroma distances to scores using `score = 1.0 - distance` for cosine distance;
- sort results by score descending and assign `rank_no` starting at `1`.

- [ ] **Step 5: Add fake-client tests for wrapper behavior**

Extend `test_vector_store.py` with a small fake collection/client to verify:

- upsert sends ids from `build_photo_vector_id()`;
- query filters and converts returned metadata;
- `has_user_images()` returns `True` only when the fake collection count/query says user vectors exist.

- [ ] **Step 6: Run focused tests**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_vector_store
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\manager\vector_store.py
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add CLIP_model\manager\vector_store.py CLIP_model\tests\test_vector_store.py
git commit -m "feat: add chroma vector store wrapper"
```

## Task 4: Integrate Chroma Into Model Service

**Files:**
- Create: `CLIP_model/manager/vector_search.py`
- Modify: `CLIP_model/client.py`
- Modify: `CLIP_model/tests/test_ai_task_payload.py`
- Add or modify: `CLIP_model/tests/test_vector_search_flow.py`

**Interfaces:**
- Consumes:
  - `encode_images(model, image_paths) -> np.ndarray`
  - `encode_texts(model, texts) -> np.ndarray`
  - `ChromaVectorStore`
  - `build_text_search_matches_from_hits(hits, description_id, allowed_photo_ids)`
- Produces:
  - `/upload` upserts image vectors when vector store is enabled and smoke mode is false.
  - `/getPhotos` uses Chroma first when enabled and falls back when needed.

- [ ] **Step 1: Write failing flow tests around fallback decision**

Create `CLIP_model/tests/test_vector_search_flow.py` with `unittest.TestCase` tests for a new function in `manager/vector_search.py`:

```python
def build_text_search_matches_with_vector_store(
    vector_store,
    model,
    description,
    description_id,
    photo_list,
    photo_id_list,
    top_k,
    fallback_enabled,
    encode_texts_fn,
    predict_fn,
):
    ...
```

Test cases:

- vector store returns hits, so `predict_fn` is not called;
- vector store raises an exception and fallback is enabled, so `predict_fn` is called;
- vector store returns no hits and fallback is enabled, so `predict_fn` is called;
- vector store returns stale `photo_id`, so stale result is ignored and fallback is used if nothing valid remains.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_vector_search_flow
```

Expected: FAIL because the helper does not exist.

- [ ] **Step 3: Add service-level helpers outside `client.py`**

Create `CLIP_model/manager/vector_search.py` with:

- `build_text_search_matches_with_vector_store(...)`.

Keep this module testable without starting Flask or loading the real CLIP model by injecting `encode_texts_fn` and `predict_fn`.

In `CLIP_model/client.py`, add:

- `build_vector_store_from_config(cfg)`;
- `try_upsert_image_embeddings(vector_store, user_id, photo_id_list, photo_list, logger)`.

- [ ] **Step 4: Update `/upload` path**

Change `func1_process()` signature to include `user_id` and `vector_store`. In non-smoke mode:

1. encode image embeddings once with `encode_images()`;
2. call `try_upsert_image_embeddings()`;
3. compute category logits using the existing `predict()` path for this first implementation.

Also update `receive_category_task()` to read required `userId` for vector upsert. If `userId` is missing, skip Chroma upsert and log a warning instead of failing the category task.

- [ ] **Step 5: Update backend DTO to send user id**

Add `userId` in:

- `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiUploadDto.java`
- `CLIP_backend/Management/src/main/java/com/hw/manage/Service/impl/CategoryServiceImpl.java`

Set `uploadDto.setUserId(userId)` before calling `/upload`.

- [ ] **Step 6: Update `/getPhotos` path**

In `func2_process()`, when not in smoke mode:

1. call `build_text_search_matches_with_vector_store()`;
2. if it returns vector-backed matches, callback them;
3. if it falls back, use current `predict()` + `build_text_search_matches()` behavior.

- [ ] **Step 7: Run focused model tests**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_vector_search_flow
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\manager\vector_search.py
```

Expected: PASS.

- [ ] **Step 8: Run backend tests if Java DTO changed**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add CLIP_model\client.py CLIP_model\manager\vector_search.py CLIP_model\tests\test_vector_search_flow.py
git add CLIP_backend\demo-pojo\src\main\java\com\hw\pojo\dto\AiUploadDto.java CLIP_backend\Management\src\main\java\com\hw\manage\Service\impl\CategoryServiceImpl.java
git commit -m "feat: use chroma for text search"
```

## Task 5: Documentation And Local Smoke Verification

**Files:**
- Modify: `CLIP_model/README.md`
- Modify: `CLIP_model/CLAUDE.md`
- Modify: `docs/local-runbook.md`
- Modify: `AGENTS.md` only if a new rule is needed for future agents

**Interfaces:**
- Consumes: final config names and local commands from Tasks 1-4.
- Produces: current docs for running Chroma locally.

- [ ] **Step 1: Update docs**

Document:

- `VECTOR_STORE_ENABLED`;
- `CHROMA_PERSIST_DIR`;
- `CHROMA_COLLECTION`;
- `EMBEDDING_MODEL_NAME`;
- `VECTOR_SEARCH_FALLBACK`;
- `CLIP_SMOKE_MODE=true` bypasses Chroma/heavy inference;
- Chroma data should be treated as rebuildable local index state.

- [ ] **Step 2: Run doc consistency checks**

Run:

```powershell
cd D:\CLIP
rg -n "DB_HOST|DB_PASSWORD|DB_NAME|db_manager|photo_description\b|idNum|Redisconfig|spring-boot-starter-data-redis|gRPC 替代" AGENTS.md docs CLIP_backend CLIP_model
```

Expected: only intentional mentions such as `photo_description_matches` and warnings not to use legacy `idNum`.

- [ ] **Step 3: Run full verification**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\server.py CLIP_model\utils\config.py CLIP_model\utils\pred.py CLIP_model\utils\embedding.py CLIP_model\manager\ai_task_payload.py CLIP_model\manager\backend_client.py CLIP_model\manager\vector_store.py CLIP_model\manager\vector_search.py

cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package
```

Expected: PASS.

- [ ] **Step 4: Manual Chroma smoke test**

Run backend and model in real mode, upload a small category batch, then submit the same text search twice. Confirm:

- model `/health` reports `cuda:0`;
- `CLIP_model/chroma_data` or configured persist directory is created;
- first search returns callback results;
- second search logs/vector path show Chroma query use;
- Java download endpoint returns `/images/...` URLs.

- [ ] **Step 5: Commit**

```powershell
git add AGENTS.md docs\local-runbook.md CLIP_model\README.md CLIP_model\CLAUDE.md
git commit -m "docs: document local chroma workflow"
```

## Final Verification

Run:

```powershell
cd D:\CLIP
git diff --check
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\server.py CLIP_model\utils\config.py CLIP_model\utils\pred.py CLIP_model\utils\embedding.py CLIP_model\manager\ai_task_payload.py CLIP_model\manager\backend_client.py CLIP_model\manager\vector_store.py CLIP_model\manager\vector_search.py

cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package
```

Then run one real GPU smoke test with backend, model, MySQL, and local Chroma enabled.
