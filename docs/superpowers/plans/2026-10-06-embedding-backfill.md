# Embedding Backfill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a recoverable embedding backfill flow that rebuilds Chroma image vectors from Java-owned MySQL photo metadata and persists vector metadata in `embedding_records`.

**Architecture:** Java creates and owns `EMBEDDING_BACKFILL` tasks, task-photo membership, and MySQL embedding metadata. Python exposes `/embeddings/backfill`, encodes images, upserts Chroma vectors, then callbacks Java at `/ai/tasks/{taskId}/embeddings`. Existing text search and match callbacks remain unchanged.

**Tech Stack:** Java Spring Boot 3.5/MyBatis/MySQL, Python Flask/PyTorch/ChromaDB/NumPy, HTTP callbacks, existing JWT security and optional `AI_CALLBACK_TOKEN`.

**Spec:** `docs/superpowers/specs/2026-10-06-embedding-backfill-design.md`

## Global Constraints

- Java backend owns MySQL, user APIs, photo metadata, AI task state, embedding metadata, and callback validation.
- Python model service owns CLIP inference, image embedding generation, Chroma upsert, and result callbacks.
- Python must not connect to or mutate MySQL directly.
- MySQL `embedding_records` stores auditable vector metadata only; Chroma stores actual vector payloads.
- Keep backend/model transport over HTTP; do not introduce WebSocket transport.
- Redis is not required and must not be reintroduced.
- Ignore `CLIP_frontend` for this phase.
- Use existing vector id format `photo:{photo_id}:{embedding_model}`.
- `CLIP_SMOKE_MODE=true` must not create fake successful embedding records.

## Review Focus

- Missing or deleted Chroma data: backfill must recreate vectors from active MySQL photo metadata and local files.
- User isolation: requested `photoIds` and callback records must be validated against active photos owned by the task user.
- Idempotency: rerunning backfill must update `embedding_records` and Chroma vectors without duplicate row failures.
- Wrong task type callback: `/ai/tasks/{taskId}/embeddings` must reject non-`EMBEDDING_BACKFILL` tasks.
- Smoke mode and disabled vector store: Python must return a clear failed callback rather than writing fake READY records.

---

## File Structure

- Create `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/EmbeddingBackfillRequestDto.java`: user request body for backfill.
- Create `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiEmbeddingBackfillDto.java`: backend-to-model request payload.
- Create `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiEmbeddingRecordDto.java`: one embedding record returned by model callback.
- Create `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiEmbeddingResultDto.java`: embedding callback payload.
- Create `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/entity/EmbeddingRecord.java`: maps `embedding_records`.
- Create `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/EmbeddingRecordMapper.java`: upsert and query embedding metadata.
- Modify `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/PhotosMapper.java`: add active-photo selection by ids and count helpers.
- Modify `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/AiTaskMapper.java`: add duplicate-safe task-photo insert if needed.
- Create `CLIP_backend/Management/src/main/java/com/hw/manage/Service/EmbeddingBackfillService.java`: service interface.
- Create `CLIP_backend/Management/src/main/java/com/hw/manage/Service/impl/EmbeddingBackfillServiceImpl.java`: creates backfill tasks and handles embedding callbacks.
- Create `CLIP_backend/Management/src/main/java/com/hw/manage/Controller/EmbeddingController.java`: `POST /user/embeddings/backfill`.
- Modify `CLIP_backend/Management/src/main/java/com/hw/manage/Controller/AiTaskController.java`: add `POST /ai/tasks/{taskId}/embeddings`.
- Create backend unit tests under `CLIP_backend/Management/src/test/java/com/hw/manage/Service/impl/`.
- Create `CLIP_model/manager/embedding_backfill_payload.py`: model-side validation and callback payload builders.
- Create `CLIP_model/manager/embedding_backfill.py`: model-side processing helper.
- Modify `CLIP_model/manager/backend_client.py`: add generic or embedding-specific callback helper.
- Modify `CLIP_model/client.py`: add `/embeddings/backfill` endpoint and background worker.
- Create `CLIP_model/tests/test_embedding_backfill_payload.py`.
- Create `CLIP_model/tests/test_embedding_backfill_flow.py`.
- Modify `docs/local-runbook.md`, `CLIP_backend/README.md`, `CLIP_model/README.md`, and `AGENTS.md` if future-agent rules need the new flow.

## Task 1: Backend DTOs And Embedding Record Mapper

**Files:**
- Create: `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/EmbeddingBackfillRequestDto.java`
- Create: `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiEmbeddingBackfillDto.java`
- Create: `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiEmbeddingRecordDto.java`
- Create: `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiEmbeddingResultDto.java`
- Create: `CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/entity/EmbeddingRecord.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/EmbeddingRecordMapper.java`
- Test: `CLIP_backend/Management/src/test/java/com/hw/manage/Mapper/EmbeddingRecordMapperTest.java`

**Interfaces:**
- Produces `EmbeddingBackfillRequestDto` with fields `String username`, `List<Long> photoIds`.
- Produces `AiEmbeddingBackfillDto` with fields `Long taskId`, `Long userId`, `List<Long> photosId`, `List<String> photosList`, `String embeddingModel`, `String vectorDb`, `String collectionName`.
- Produces `AiEmbeddingRecordDto` with fields `Long photoId`, `String targetType`, `String embeddingModel`, `String vectorDb`, `String collectionName`, `String vectorId`, `Integer dim`, `String status`.
- Produces `AiEmbeddingResultDto` with fields `Long taskId`, `String status`, `String errorMessage`, `List<AiEmbeddingRecordDto> records`.
- Produces `EmbeddingRecordMapper.upsert(EmbeddingRecord record)`.
- Produces `EmbeddingRecordMapper.countReadyByUserAndModel(Long userId, String embeddingModel)`.

- [ ] **Step 1: Write failing mapper test**

Create `EmbeddingRecordMapperTest` as a `@MybatisTest` or `@SpringBootTest` using the configured local test database only if the project already supports that. If isolated mapper tests are not practical, write a unit-style SQL assertion test that verifies `EmbeddingRecordMapper.class` has an `upsert(EmbeddingRecord)` method and that `EmbeddingRecord` exposes the fields in the Interfaces block.

Test name: `upsertEmbeddingRecordUsesTargetAndModelAsIdempotencyKey`.

Assert that calling `upsert` twice for `targetType="PHOTO"`, `targetId=11`, `embeddingModel="clip-vit-l-14"` leaves one logical row with the second `vectorId` and `status="READY"`.

- [ ] **Step 2: Run the focused backend test and verify RED**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q -pl Management -Dtest=EmbeddingRecordMapperTest test
```

Expected: FAIL because DTOs, entity, or mapper do not exist.

- [ ] **Step 3: Add DTOs and entity**

Create the DTO and entity classes listed in Files. Use Lombok `@Data`. Use `Integer dim`, not primitive `int`, so callback validation can detect missing dimensions.

- [ ] **Step 4: Add `EmbeddingRecordMapper`**

Implement `upsert(EmbeddingRecord record)` with MySQL `ON DUPLICATE KEY UPDATE` against the existing `uk_embedding_target_model` unique key. Update `vector_db`, `collection_name`, `vector_id`, `dim`, `status`, and `updated_at`.

Also add:

```java
@Select("SELECT count(*) FROM embedding_records WHERE user_id = #{userId} AND embedding_model = #{embeddingModel} AND status = 'READY'")
Integer countReadyByUserAndModel(@Param("userId") Long userId, @Param("embeddingModel") String embeddingModel);
```

- [ ] **Step 5: Run focused backend test**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q -pl Management -Dtest=EmbeddingRecordMapperTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add CLIP_backend\demo-pojo\src\main\java\com\hw\pojo\dto\EmbeddingBackfillRequestDto.java CLIP_backend\demo-pojo\src\main\java\com\hw\pojo\dto\AiEmbeddingBackfillDto.java CLIP_backend\demo-pojo\src\main\java\com\hw\pojo\dto\AiEmbeddingRecordDto.java CLIP_backend\demo-pojo\src\main\java\com\hw\pojo\dto\AiEmbeddingResultDto.java CLIP_backend\demo-pojo\src\main\java\com\hw\pojo\entity\EmbeddingRecord.java CLIP_backend\Management\src\main\java\com\hw\manage\Mapper\EmbeddingRecordMapper.java CLIP_backend\Management\src\test\java\com\hw\manage\Mapper\EmbeddingRecordMapperTest.java
git commit -m "feat: add embedding record mapper"
```

## Task 2: Backend Backfill Request Service

**Files:**
- Modify: `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/PhotosMapper.java`
- Modify: `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/AiTaskMapper.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Service/EmbeddingBackfillService.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Service/impl/EmbeddingBackfillServiceImpl.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Controller/EmbeddingController.java`
- Test: `CLIP_backend/Management/src/test/java/com/hw/manage/Service/impl/EmbeddingBackfillServiceImplTest.java`

**Interfaces:**
- Consumes DTOs and mapper from Task 1.
- Produces `EmbeddingBackfillService.startBackfill(EmbeddingBackfillRequestDto request) -> Map<String, Object>`.
- Produces `POST /user/embeddings/backfill`.
- Produces `PhotosMapper.listActivePhotoRecordsByIds(Long userId, List<Long> photoIds)`.

- [ ] **Step 1: Write failing service tests**

Create `EmbeddingBackfillServiceImplTest` with Mockito.

Test `startBackfillUsesAllActivePhotosWhenPhotoIdsAreMissing`:

- mock `UserMapper.findByUsername("alice")` to return user id `7`;
- mock `PhotosMapper.listActivePhotoRecords(7L)` to return two active `Photos` with ids `11`, `12`;
- capture inserted `AiTask` and assert `taskType="EMBEDDING_BACKFILL"` and `status="PENDING"`;
- verify `AiTaskMapper.addPhoto(taskId, 11L)` and `addPhoto(taskId, 12L)`;
- capture `AiEmbeddingBackfillDto` sent to `RestTemplate.postForEntity`;
- assert URL ends with `/embeddings/backfill`, `userId=7`, `photosId=[11,12]`, and paths match the photo `storagePath` values.

Test `startBackfillRejectsPhotoIdsOutsideActiveUserSet`:

- request photo ids `[11, 99]`;
- mock mapper to return only photo `11`;
- assert `IllegalArgumentException` or `RuntimeException` with a clear message;
- verify no model call.

- [ ] **Step 2: Run focused service test and verify RED**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q -pl Management -Dtest=EmbeddingBackfillServiceImplTest test
```

Expected: FAIL because service/controller methods do not exist.

- [ ] **Step 3: Add photo mapper helper**

In `PhotosMapper`, add:

```java
@Select({
    "<script>",
    "SELECT * FROM photos WHERE user_id = #{userId} AND status = 'ACTIVE' AND id IN",
    "<foreach collection='photoIds' item='photoId' open='(' separator=',' close=')'>#{photoId}</foreach>",
    "ORDER BY created_at DESC",
    "</script>"
})
List<Photos> listActivePhotoRecordsByIds(@Param("userId") Long userId, @Param("photoIds") List<Long> photoIds);
```

- [ ] **Step 4: Implement service**

`EmbeddingBackfillServiceImpl.startBackfill(...)` should:

1. resolve the user by username;
2. select all active photos when `photoIds` is null or empty;
3. select active photos by ids when ids are provided and validate count equality;
4. reject empty photo sets;
5. create `AiTask` with `EMBEDDING_BACKFILL` and `PENDING`;
6. insert `ai_task_photos` rows;
7. send `AiEmbeddingBackfillDto` to `${ai.service.base-url}/embeddings/backfill`;
8. require response `Result.code == 1`;
9. mark task `RUNNING`;
10. return map with `taskId`, `photoCount`, and `msg`.

Use config values:

- `ai.embedding.model` default `clip-vit-l-14`;
- `ai.vector.db` default `chroma`;
- `ai.vector.collection` default `clip_image_embeddings`.

Add those defaults in `application.yml` under `ai`.

- [ ] **Step 5: Add controller endpoint**

Create `EmbeddingController`:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/embeddings")
public class EmbeddingController {
    @PostMapping("/backfill")
    public Result backfill(@RequestBody EmbeddingBackfillRequestDto request) { ... }
}
```

Return `Result.success(service.startBackfill(request))`; catch exceptions consistently with existing controllers.

- [ ] **Step 6: Run focused backend tests**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q -pl Management -Dtest=EmbeddingBackfillServiceImplTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add CLIP_backend\Management\src\main\java\com\hw\manage\Mapper\PhotosMapper.java CLIP_backend\Management\src\main\java\com\hw\manage\Mapper\AiTaskMapper.java CLIP_backend\Management\src\main\java\com\hw\manage\Service\EmbeddingBackfillService.java CLIP_backend\Management\src\main\java\com\hw\manage\Service\impl\EmbeddingBackfillServiceImpl.java CLIP_backend\Management\src\main\java\com\hw\manage\Controller\EmbeddingController.java CLIP_backend\Management\src\main\resources\application.yml CLIP_backend\Management\src\test\java\com\hw\manage\Service\impl\EmbeddingBackfillServiceImplTest.java
git commit -m "feat: create embedding backfill tasks"
```

## Task 3: Backend Embedding Callback Handling

**Files:**
- Modify: `CLIP_backend/Management/src/main/java/com/hw/manage/Controller/AiTaskController.java`
- Modify: `CLIP_backend/Management/src/main/java/com/hw/manage/Service/EmbeddingBackfillService.java`
- Modify: `CLIP_backend/Management/src/main/java/com/hw/manage/Service/impl/EmbeddingBackfillServiceImpl.java`
- Modify: `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/AiTaskMapper.java` if a task-photo membership helper is missing.
- Test: `CLIP_backend/Management/src/test/java/com/hw/manage/Service/impl/EmbeddingBackfillCallbackTest.java`

**Interfaces:**
- Consumes `AiEmbeddingResultDto` and `EmbeddingRecordMapper.upsert(...)` from Task 1.
- Produces `EmbeddingBackfillService.saveEmbeddingResult(Long taskId, AiEmbeddingResultDto result)`.
- Produces `POST /ai/tasks/{taskId}/embeddings`.

- [ ] **Step 1: Write failing callback tests**

Create `EmbeddingBackfillCallbackTest` with Mockito.

Test `saveEmbeddingResultUpsertsRecordsAndMarksSuccess`:

- mock `AiTaskMapper.findById(31L)` to return task type `EMBEDDING_BACKFILL`, user id `7`;
- callback has one record for `photoId=11`, `vectorId="photo:11:clip-vit-l-14"`, `dim=768`, `status="READY"`;
- mock task-photo membership count for `taskId=31`, `photoId=11` as `1`;
- verify `EmbeddingRecordMapper.upsert(...)` receives `targetType="PHOTO"`, `targetId=11`, `userId=7`;
- verify task status updated to `SUCCESS`.

Test `saveEmbeddingResultRejectsWrongTaskType`:

- task type `TEXT_SEARCH`;
- assert exception and no upsert.

Test `saveEmbeddingResultMarksFailedOnFailureCallback`:

- callback `status="FAILED"` and `errorMessage="vector store disabled"`;
- verify task status `FAILED` and no upsert.

- [ ] **Step 2: Run focused callback test and verify RED**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q -pl Management -Dtest=EmbeddingBackfillCallbackTest test
```

Expected: FAIL because callback method does not exist.

- [ ] **Step 3: Add membership helper if needed**

If `PhotoDescriptionMapper.countTaskPhoto(...)` is too semantically tied to match validation, add to `AiTaskMapper`:

```java
@Select("SELECT count(*) FROM ai_task_photos WHERE task_id = #{taskId} AND photo_id = #{photoId}")
Integer countTaskPhoto(@Param("taskId") Long taskId, @Param("photoId") Long photoId);
```

- [ ] **Step 4: Implement callback service**

`saveEmbeddingResult(...)` should:

1. validate body `taskId` matches path when body value is present;
2. load task and require `taskType="EMBEDDING_BACKFILL"`;
3. on `FAILED`, update task status and return;
4. require non-empty `records`;
5. validate each record has `photoId`, `targetType="PHOTO"`, `embeddingModel`, `vectorDb`, `collectionName`, `vectorId`, `dim`, and `status`;
6. require each photo belongs to the task through `ai_task_photos`;
7. map each record to `EmbeddingRecord` with task user id and `targetId=photoId`;
8. upsert all records;
9. mark task `SUCCESS`.

- [ ] **Step 5: Add callback endpoint**

In `AiTaskController`, add:

```java
@PostMapping("/{taskId}/embeddings")
public Result saveEmbeddings(@PathVariable Long taskId,
                             @RequestBody AiEmbeddingResultDto result,
                             @RequestHeader(value = "X-AI-Callback-Token", required = false) String callbackToken) { ... }
```

Reuse existing `validateCallbackToken(callbackToken)`.

- [ ] **Step 6: Run focused callback test**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q -pl Management -Dtest=EmbeddingBackfillCallbackTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add CLIP_backend\Management\src\main\java\com\hw\manage\Controller\AiTaskController.java CLIP_backend\Management\src\main\java\com\hw\manage\Service\EmbeddingBackfillService.java CLIP_backend\Management\src\main\java\com\hw\manage\Service\impl\EmbeddingBackfillServiceImpl.java CLIP_backend\Management\src\main\java\com\hw\manage\Mapper\AiTaskMapper.java CLIP_backend\Management\src\test\java\com\hw\manage\Service\impl\EmbeddingBackfillCallbackTest.java
git commit -m "feat: persist embedding callback records"
```

## Task 4: Python Backfill Payload And Processor

**Files:**
- Create: `CLIP_model/manager/embedding_backfill_payload.py`
- Create: `CLIP_model/manager/embedding_backfill.py`
- Modify: `CLIP_model/manager/backend_client.py`
- Test: `CLIP_model/tests/test_embedding_backfill_payload.py`
- Test: `CLIP_model/tests/test_embedding_backfill_flow.py`

**Interfaces:**
- Produces `validate_backfill_request(data: dict) -> dict`.
- Produces `build_embedding_records(photo_ids, embedding_model, vector_db, collection_name, dim) -> list[dict]`.
- Produces `build_embedding_result_payload(task_id, status, records=None, error_message=None) -> dict`.
- Produces `process_embedding_backfill(vector_store, model, request_data, encode_images_fn, logger=None) -> dict`.
- Produces `post_embedding_result(task_id, payload, backend_base_url, callback_token=None, post=requests.post, logger=None)`.

- [ ] **Step 1: Write failing payload tests**

Create `test_embedding_backfill_payload.py`:

- `test_validate_backfill_request_rejects_missing_required_fields`;
- `test_validate_backfill_request_rejects_mismatched_photo_lengths`;
- `test_build_embedding_records_uses_photo_vector_ids_and_dim`;
- `test_build_embedding_result_payload_matches_backend_contract`.

Assert record shape exactly:

```python
{
    "photoId": 11,
    "targetType": "PHOTO",
    "embeddingModel": "clip-vit-l-14",
    "vectorDb": "chroma",
    "collectionName": "clip_image_embeddings",
    "vectorId": "photo:11:clip-vit-l-14",
    "dim": 768,
    "status": "READY",
}
```

- [ ] **Step 2: Run payload tests and verify RED**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding_backfill_payload
```

Expected: FAIL because module does not exist.

- [ ] **Step 3: Implement payload module**

Implement `embedding_backfill_payload.py`. Reuse `build_photo_vector_id()` from `manager.vector_store`.

`validate_backfill_request` should return normalized values with integer `taskId`, integer `userId`, integer list `photosId`, and string list `photosList`.

- [ ] **Step 4: Write failing processor tests**

Create `test_embedding_backfill_flow.py` with fake vector store and fake `encode_images_fn`.

Test `process_backfill_upserts_vectors_and_returns_ready_records`:

- fake encode returns NumPy array shape `(2, 3)`;
- assert vector store receives `user_id=7`, photo ids, paths, embeddings;
- assert result payload has two READY records with `dim=3`.

Test `process_backfill_fails_when_vector_store_unavailable`:

- `vector_store=None`;
- assert result status `FAILED` and error message mentions vector store.

- [ ] **Step 5: Run processor tests and verify RED**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding_backfill_flow
```

Expected: FAIL because processor does not exist.

- [ ] **Step 6: Implement processor and callback client**

In `embedding_backfill.py`, implement `process_embedding_backfill(...)`:

1. validate request;
2. fail if vector store is `None`;
3. encode image embeddings;
4. infer `dim` from `embeddings.shape[1]`;
5. call `vector_store.upsert_image_embeddings(...)`;
6. return success payload from `build_embedding_result_payload`.

In `backend_client.py`, add `post_embedding_result(...)` that posts to:

```text
{BACKEND_BASE_URL}/ai/tasks/{taskId}/embeddings
```

Keep token header behavior identical to `post_task_result(...)`.

- [ ] **Step 7: Run focused Python tests**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding_backfill_payload
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding_backfill_flow
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add CLIP_model\manager\embedding_backfill_payload.py CLIP_model\manager\embedding_backfill.py CLIP_model\manager\backend_client.py CLIP_model\tests\test_embedding_backfill_payload.py CLIP_model\tests\test_embedding_backfill_flow.py
git commit -m "feat: add embedding backfill processor"
```

## Task 5: Python `/embeddings/backfill` Endpoint

**Files:**
- Modify: `CLIP_model/client.py`
- Modify: `CLIP_model/tests/test_embedding_backfill_flow.py` if endpoint helper coverage is added.

**Interfaces:**
- Consumes `process_embedding_backfill(...)` and `post_embedding_result(...)` from Task 4.
- Produces `POST /embeddings/backfill`.

- [ ] **Step 1: Write failing endpoint helper test**

If importing `client.py` remains too expensive because it loads CLIP weights, keep endpoint behavior covered through a small helper in Task 4 and add a smoke-free test for `func_embedding_backfill_process(...)` only after extracting that helper from `client.py`.

Test name: `embeddingBackfillProcessPostsSuccessPayload`.

Assert the helper calls `post_embedding_result` with `/ai/tasks/{taskId}/embeddings` payload when the processor succeeds.

- [ ] **Step 2: Run test and verify RED**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding_backfill_flow
```

Expected: FAIL because endpoint/process helper is not wired.

- [ ] **Step 3: Add endpoint**

In `client.py`, add:

```python
@app.route("/embeddings/backfill", methods=["POST"])
def receive_embedding_backfill_task():
    ...
```

The endpoint should validate JSON with `validate_backfill_request(data)`, start a background thread, and return:

```json
{"code": 1, "message": "embedding backfill task accepted"}
```

On validation error, return HTTP 400 with `code=0`.

- [ ] **Step 4: Add background worker**

Add `func_embedding_backfill_process(task_id, request_data, vector_store, logger)`:

- if `cfg.CLIP_SMOKE_MODE` is true, post failed embedding result with error `Embedding backfill requires real CLIP mode`;
- otherwise call `process_embedding_backfill(...)`;
- post result with `post_embedding_result(...)`;
- on unexpected exception, post failed result.

- [ ] **Step 5: Run focused and full model tests**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_embedding_backfill_flow
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\manager\embedding_backfill.py CLIP_model\manager\embedding_backfill_payload.py CLIP_model\manager\backend_client.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add CLIP_model\client.py CLIP_model\tests\test_embedding_backfill_flow.py
git commit -m "feat: expose embedding backfill endpoint"
```

## Task 6: Documentation And Full Verification

**Files:**
- Modify: `docs/local-runbook.md`
- Modify: `CLIP_backend/README.md`
- Modify: `CLIP_model/README.md`
- Modify: `AGENTS.md` if future-agent rules should mention backfill.

**Interfaces:**
- Consumes final endpoint names and commands from Tasks 1-5.
- Produces documented local rebuild workflow.

- [ ] **Step 1: Update docs**

Document:

- `POST /user/embeddings/backfill`;
- `POST /embeddings/backfill`;
- `POST /ai/tasks/{taskId}/embeddings`;
- `embedding_records` as MySQL metadata for Chroma vectors;
- Chroma data is rebuildable and can be deleted/recreated through backfill;
- `CLIP_SMOKE_MODE=true` does not create fake successful embedding records.

- [ ] **Step 2: Run docs consistency search**

Run:

```powershell
cd D:\CLIP
rg -n "idNum|db_manager|Redisconfig|spring-boot-starter-data-redis|WebSocket|websocket|direct MySQL|photo_description\\b" AGENTS.md docs CLIP_backend CLIP_model
```

Expected: only intentional mentions such as warnings not to use legacy names and `photo_description_matches`.

- [ ] **Step 3: Run model verification**

Run:

```powershell
cd D:\CLIP
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\utils\config.py CLIP_model\utils\pred.py CLIP_model\utils\embedding.py CLIP_model\manager\ai_task_payload.py CLIP_model\manager\backend_client.py CLIP_model\manager\vector_store.py CLIP_model\manager\vector_search.py CLIP_model\manager\embedding_backfill.py CLIP_model\manager\embedding_backfill_payload.py
```

Expected: PASS.

- [ ] **Step 4: Run backend verification**

Run:

```powershell
cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package
```

Expected: PASS.

- [ ] **Step 5: Manual GPU backfill smoke**

Run backend and model in real mode. Then:

1. ensure at least one active photo exists for a test user;
2. delete or move `D:\CLIP\CLIP_model\chroma_data`;
3. call `POST /user/embeddings/backfill`;
4. wait for the task to reach `SUCCESS`;
5. query MySQL:

```sql
SELECT target_type, target_id, embedding_model, vector_db, collection_name, vector_id, dim, status
FROM embedding_records
WHERE user_id = <test_user_id>
ORDER BY target_id;
```

6. run text search and confirm `CLIP_model/logs/client.log` contains `文本搜图命中 Chroma`.

- [ ] **Step 6: Commit**

```powershell
git add docs\local-runbook.md CLIP_backend\README.md CLIP_model\README.md AGENTS.md
git commit -m "docs: document embedding backfill workflow"
```

## Final Verification

Run:

```powershell
cd D:\CLIP
git diff --check
D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests
D:\Anaconda\envs\CLIP\python.exe -m py_compile CLIP_model\client.py CLIP_model\utils\config.py CLIP_model\utils\pred.py CLIP_model\utils\embedding.py CLIP_model\manager\ai_task_payload.py CLIP_model\manager\backend_client.py CLIP_model\manager\vector_store.py CLIP_model\manager\vector_search.py CLIP_model\manager\embedding_backfill.py CLIP_model\manager\embedding_backfill_payload.py

cd D:\CLIP\CLIP_backend
$env:MYSQL_PASSWORD='<local mysql password>'
mvn -q test
mvn -q -DskipTests package
```

Then run the manual GPU backfill smoke test from Task 6.
