# AI Task Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Provide authenticated task and embedding inspection, correlated lifecycle logs, and protected metrics for operating and benchmarking AI tasks on a server.

**Architecture:** MySQL remains the source of truth. Java exposes read-only task and embedding views scoped to the JWT-authenticated user; Java and Python share a key=value backfill lifecycle log format; Prometheus aggregates bounded-cardinality outcomes and latency.

**Tech Stack:** Java 23, Spring Boot 3.5, MyBatis, MySQL, Spring Actuator/Micrometer, Python Flask and prometheus_client.

**Spec:** docs/superpowers/plans/2026-10-06-observability.md (this user-approved plan).

## Global Constraints

- Java owns MySQL. Python must never query or write Java-owned business tables.
- Keep backend-model transport over HTTP; do not introduce WebSocket or Redis.
- Do not change the database schema in this phase.
- Every /user inspection API derives ownership from JWT identity, never a submitted username or user ID.
- Metrics are trusted internal monitoring endpoints, never anonymous public APIs.
- Prometheus labels must not contain task ID, user ID, photo ID, username, file path, text query, or exception text.
- Do not add a frontend debug console.

## Review Focus

- A user requesting another user's task ID receives no task data and learns nothing about its existence.
- Bad or oversized pagination cannot return the whole embedding table.
- PENDING and RUNNING are never counted as terminal samples.
- Callback failure logs task correlation data but never logs tokens or full payloads.
- High task/user count produces a bounded number of metric series.

---

## File Structure

- Create DTOs AiTaskStatusDto, EmbeddingRecordDto, and EmbeddingRecordQueryDto in CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto.
- Create AiTaskObservationService and EmbeddingObservationService with implementations under the existing Service structure.
- Create AiTaskObservationController; extend EmbeddingController.
- Extend AiTaskMapper and EmbeddingRecordMapper with ownership-scoped read/count queries.
- Create CLIP_backend/Management/src/main/java/com/hw/manage/observability/AiTaskMetrics.java.
- Modify EmbeddingBackfillServiceImpl, AiTaskController, CLIP_model/client.py, and CLIP_model/manager/embedding_backfill.py for logs and metrics.
- Modify Management pom.xml, application.yml, and CLIP_model/utils/config.py for exporters.
- Add Java Mockito tests and Python unittests; update local-runbook, backend/model README, and Apifox guide.

## Metric Contract

| Component | Metric | Type | Labels | Meaning |
| --- | --- | --- | --- | --- |
| Java | clip_ai_task_transitions_total | Counter | task_type,status | Persisted transition to RUNNING, SUCCESS, or FAILED. |
| Java | clip_ai_task_terminal_duration_seconds | Timer | task_type,status | Creation-to-terminal-callback duration; terminal only. |
| Java | clip_embedding_records_upserted_total | Counter | embedding_model,vector_db,status | Callback records persisted by Java. |
| Python | clip_embedding_backfill_jobs_total | Counter | outcome | Worker result: success, failed, smoke_rejected. |
| Python | clip_embedding_backfill_duration_seconds | Histogram | outcome | Model worker duration. |
| Python | clip_embedding_backfill_photos_total | Counter | outcome | Images attempted by completed workers. |
| Python | clip_embedding_callback_failures_total | Counter | outcome | Callback-post exceptions; fixed outcome exception. |

### Task 1: Authenticated AI Task Status API

**Files:**
- Create: CLIP_backend/demo-pojo/src/main/java/com/hw/pojo/dto/AiTaskStatusDto.java
- Create: CLIP_backend/Management/src/main/java/com/hw/manage/Service/AiTaskObservationService.java
- Create: CLIP_backend/Management/src/main/java/com/hw/manage/Service/impl/AiTaskObservationServiceImpl.java
- Create: CLIP_backend/Management/src/main/java/com/hw/manage/Controller/AiTaskObservationController.java
- Modify: CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/AiTaskMapper.java
- Test: CLIP_backend/Management/src/test/java/com/hw/manage/Service/impl/AiTaskObservationServiceImplTest.java

**Interfaces:**
- AiTaskStatusDto fields: Long taskId, String taskType, String status, String errorMessage, Integer photoCount, LocalDateTime createdAt, LocalDateTime updatedAt. It never exposes userId.
- AiTaskObservationService.getTaskStatus(Long taskId, String username) returns AiTaskStatusDto.
- GET /user/ai-tasks/{taskId} returns Result.success(AiTaskStatusDto).
- Add AiTaskMapper.countTaskPhotos(Long taskId).

- [ ] **Step 1: Write the failing service tests**

Create getTaskStatusReturnsOnlyTheCurrentUsersTask. User alice resolves to ID 7; findByIdAndUserId(31, 7) returns task 31; countTaskPhotos(31) returns 2. Assert all status fields and photoCount are mapped.

Create getTaskStatusRejectsMissingOrForeignTask. Missing user or null ownership-scoped mapper result raises IllegalArgumentException. Verify it never falls back to unscoped findById.

- [ ] **Step 2: Run the focused test and verify RED**

Run: cd D:/CLIP/CLIP_backend; mvn -q -pl Management -Dtest=AiTaskObservationServiceImplTest test

Expected: FAIL because observation types are missing.

- [ ] **Step 3: Implement mapper, DTO, and service**

Add SELECT count(*) FROM ai_task_photos WHERE task_id = #{taskId}. Resolve the principal name through UserMapper and load only through findByIdAndUserId. Reject null/non-positive IDs. Use one error message, AI任务不存在或无权访问, for absent and foreign tasks.

- [ ] **Step 4: Implement the controller**

Create GET /user/ai-tasks/{taskId} accepting PathVariable Long taskId and Spring Authentication. Pass only authentication.getName() to the service. Do not accept username in a path, query, or request body.

- [ ] **Step 5: Verify and commit**

Run:
cd D:/CLIP/CLIP_backend
mvn -q -pl Management -Dtest=AiTaskObservationServiceImplTest test
mvn -q test

Expected: PASS.

Stage the DTO, mapper, service, controller, and test explicitly. Commit message: feat: expose authenticated AI task status.

### Task 2: Paginated User Embedding Record API

**Files:**
- Create: EmbeddingRecordDto.java and EmbeddingRecordQueryDto.java in the DTO package.
- Create: EmbeddingObservationService.java and EmbeddingObservationServiceImpl.java.
- Modify: EmbeddingRecordMapper.java and EmbeddingController.java.
- Test: CLIP_backend/Management/src/test/java/com/hw/manage/Service/impl/EmbeddingObservationServiceImplTest.java.

**Interfaces:**
- Query fields: String status, String embeddingModel, Integer page, Integer pageSize.
- Record fields: targetId, targetType, embeddingModel, vectorDb, collectionName, vectorId, dim, status, createdAt, updatedAt. No userId.
- EmbeddingObservationService.listRecords(String username, EmbeddingRecordQueryDto query) returns PageResult<EmbeddingRecordDto>.
- GET /user/embeddings?status=READY&embeddingModel=clip-vit-l-14&page=1&pageSize=20.
- Mapper list/count SQL first filters user_id, then optionally status and embedding_model.

- [ ] **Step 1: Write the failing service tests**

Create listRecordsAppliesCurrentUserAndOptionalFilters. Alice resolves to ID 7; null page/pageSize normalizes to 1/20; mapper receives READY and clip-vit-l-14; DTO result excludes user ID.

Create listRecordsRejectsInvalidPagination. Reject page 0, pageSize 0, and pageSize 101. Fixed maximum is 100.

- [ ] **Step 2: Run the focused test and verify RED**

Run: cd D:/CLIP/CLIP_backend; mvn -q -pl Management -Dtest=EmbeddingObservationServiceImplTest test

Expected: FAIL because query DTO, mapper methods, and service are missing.

- [ ] **Step 3: Implement mapper and pagination service**

Add MyBatis script list/count queries filtered by user_id and optional nonblank filters, ordered updated_at DESC then id DESC. Default to page 1 and size 20; enforce 1 through 100. Call PageHelper.startPage before the list query, map entities to DTOs, and retain PageResult.total as total record count.

- [ ] **Step 4: Add the GET endpoint**

Add GET to existing EmbeddingController, binding EmbeddingRecordQueryDto as ModelAttribute and using Authentication.getName(). Return Result.success(service.listRecords(...)). Preserve POST /user/embeddings/backfill unchanged.

- [ ] **Step 5: Verify and commit**

Run:
cd D:/CLIP/CLIP_backend
mvn -q -pl Management -Dtest=EmbeddingObservationServiceImplTest test
mvn -q test

Expected: PASS.

Stage only Task 2 files and commit feat: expose paginated embedding records.

### Task 3: Correlated Embedding-Backfill Lifecycle Logs

**Files:**
- Modify: EmbeddingBackfillServiceImpl.java, AiTaskController.java, CLIP_model/client.py, CLIP_model/manager/embedding_backfill.py.
- Modify tests: EmbeddingBackfillServiceImplTest.java, EmbeddingBackfillCallbackTest.java, CLIP_model/tests/test_embedding_backfill_flow.py.

**Interfaces:**
- Use logfmt-like key=value messages.
- Java events: embedding_backfill.created, embedding_backfill.dispatched, embedding_backfill.callback_received, embedding_backfill.completed, embedding_backfill.failed.
- Python events: embedding_backfill.accepted, embedding_backfill.started, embedding_backfill.completed, embedding_backfill.failed, embedding_backfill.callback_failed.
- Applicable keys: event, task_id, user_id, photo_count, status, duration_ms. Never log callback tokens, whole payloads, usernames, paths, or image content.

- [ ] **Step 1: Write failing log-capture tests**

Use a Java test appender/captured logger. Assert creation includes event=embedding_backfill.created, task_id=31, user_id=7, photo_count=2, and no storage path. Assert success callback logs completed with status=SUCCESS; failed callback logs failed with status=FAILED.

Use Python logging capture. Assert a worker logs one started event and one terminal event. Force callback post exception and assert callback_failed without callback token.

- [ ] **Step 2: Run focused tests and verify RED**

Run:
cd D:/CLIP/CLIP_backend
mvn -q -pl Management -Dtest=EmbeddingBackfillServiceImplTest,EmbeddingBackfillCallbackTest test
cd D:/CLIP
D:/Anaconda/envs/CLIP/python.exe -m unittest CLIP_model.tests.test_embedding_backfill_flow

Expected: FAIL because lifecycle events are absent or inconsistent.

- [ ] **Step 3: Implement Java lifecycle events**

Log created after task and task-photo persistence; dispatched after model acceptance; callback_received after token validation; completed after records persist and task is SUCCESS; failed on dispatch or callback failure. Derive terminal duration from task.createdAt only if available; otherwise omit duration.

- [ ] **Step 4: Implement Python lifecycle events**

Emit accepted only after request validation. In run_embedding_backfill_task, start a monotonic timer, emit started, then exactly one completed or failed event. Wrap callback post separately and on exception emit callback_failed with exception class only.

- [ ] **Step 5: Verify and commit**

Run:
cd D:/CLIP/CLIP_backend
mvn -q -pl Management -Dtest=EmbeddingBackfillServiceImplTest,EmbeddingBackfillCallbackTest test
cd D:/CLIP
D:/Anaconda/envs/CLIP/python.exe -m unittest CLIP_model.tests.test_embedding_backfill_flow
D:/Anaconda/envs/CLIP/python.exe -m py_compile CLIP_model/client.py CLIP_model/manager/embedding_backfill.py

Expected: PASS.

Stage Task 3 files and commit feat: add correlated embedding task logs.

### Task 4: Protected Prometheus Metrics and Server-Test Runbook

**Files:**
- Create: CLIP_backend/Management/src/main/java/com/hw/manage/observability/AiTaskMetrics.java.
- Modify: CLIP_backend/Management/pom.xml, application.yml, EmbeddingBackfillServiceImpl.java.
- Modify: CLIP_model/utils/config.py and CLIP_model/client.py.
- Create: CLIP_backend/Management/src/test/java/com/hw/manage/observability/AiTaskMetricsTest.java and CLIP_model/tests/test_metrics_endpoint.py.
- Modify: docs/local-runbook.md, CLIP_backend/README.md, CLIP_model/README.md, docs/apifox-debugging.md.

**Interfaces:**
- Java exposes authenticated GET /actuator/prometheus. Do not add an anonymous actuator security matcher.
- Python GET /metrics returns 404 when disabled. When enabled it requires X-Metrics-Token equal to nonempty METRICS_TOKEN, otherwise returns 401.
- Java facade methods: recordTransition(taskType, status), recordTerminalDuration(taskType, status, Duration), recordEmbeddingUpserts(embeddingModel, vectorDb, status, count).
- Python config: METRICS_ENABLED reads false by default; METRICS_TOKEN defaults to empty.

- [ ] **Step 1: Write failing metrics tests**

With Java SimpleMeterRegistry, assert SUCCESS records the transition, one duration observation, and exact upsert count under Metric Contract tags. Assert no task_id or user_id tag exists.

With Flask test client, assert /metrics is 404 disabled, 401 when enabled with absent/wrong token, and Prometheus text containing clip_embedding_backfill_jobs_total when enabled with correct token after a controlled worker outcome.

- [ ] **Step 2: Run focused tests and verify RED**

Run:
cd D:/CLIP/CLIP_backend
mvn -q -pl Management -Dtest=AiTaskMetricsTest test
cd D:/CLIP
D:/Anaconda/envs/CLIP/python.exe -m unittest CLIP_model.tests.test_metrics_endpoint

Expected: FAIL because exporters and metrics facade do not exist.

- [ ] **Step 3: Add Java dependencies, config, and facade**

Add spring-boot-starter-actuator and micrometer-registry-prometheus to Management. Expose health,prometheus via management endpoints; configure health details as never. Do not permit anonymous access. Implement AiTaskMetrics around injected MeterRegistry using only Metric Contract names/tags.

- [ ] **Step 4: Instrument Java state transitions**

Inject AiTaskMetrics into EmbeddingBackfillServiceImpl. Record RUNNING after model acceptance. Record exactly one terminal transition/duration for each success or failed callback. Increment upserts only after persistence succeeds. Immediate dispatch rejection records FAILED but no duration if insertion did not populate createdAt.

- [ ] **Step 5: Add guarded Python exporter and worker metrics**

Use prometheus_client generate_latest and CONTENT_TYPE_LATEST. Compare token with hmac.compare_digest and reject empty configured token. Build a test-resettable registry or dependency-injected metrics helper so repeated unittest imports do not duplicate registration. Record one outcome/duration and photo total per worker; record callback failure only when callback posting raises.

- [ ] **Step 6: Document reproducible server measurement**

Document authenticated read APIs, internal-only metrics deployment through private binding or reverse-proxy/network allowlist, all metric names/labels, and this sequence: record CPU/RAM/disk and GPU using nvidia-smi; submit known real-mode image count; poll task to terminal; scrape exporters; keep task ID and matching logs. Include formulas: throughput = successful images / elapsed seconds; failure rate = failed tasks / terminal tasks; p95 comes from histogram quantiles. Do not claim GPU utilization is exported by this work.

- [ ] **Step 7: Verify and commit**

Run:
cd D:/CLIP/CLIP_backend
mvn -q test
mvn -q -DskipTests package
cd D:/CLIP
D:/Anaconda/envs/CLIP/python.exe -m unittest discover -s CLIP_model/tests
D:/Anaconda/envs/CLIP/python.exe -m py_compile CLIP_model/client.py CLIP_model/utils/config.py CLIP_model/manager/embedding_backfill.py

Then run the documented real GPU/Chroma measurement once using a disposable test user. Verify status, record API, exporters, and logs share the task ID. Do not delete an existing Chroma collection.

Expected: all automated checks pass; manual record has task ID, image count, elapsed time, terminal status, and both exporter responses.

Stage Task 4 files and commit feat: add protected AI task metrics.

## Final Review

- [ ] Run git diff --check.
- [ ] Confirm every new /user read endpoint requires JWT and uses Authentication.getName().
- [ ] Confirm Actuator stays non-anonymous and model metrics reject missing token.
- [ ] Confirm metric labels exactly match the Metric Contract and are bounded.
- [ ] Add the two user read APIs to the existing Apifox collection; do not expose model callbacks or metrics as public client calls.

