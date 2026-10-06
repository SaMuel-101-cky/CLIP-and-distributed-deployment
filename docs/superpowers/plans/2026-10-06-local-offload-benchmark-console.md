# Local CLIP Offload Benchmark Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a local-only React and Flask experiment console that measures CLIP component offloading against a token-protected AutoDL model endpoint and records reproducible benchmark evidence locally.

**Architecture:** Create a new `CLIP_benchmark` Python package with an immutable execution-plan model, a serial Flask runner, a Java-backend persistence client, and a Vite React UI. Refactor the model offload transport into an instrumented handler that receives a runtime plan without environment mutation; extend the remote Flask server with a single guarded Tensor-RPC wrapper that returns bounded timing/size metadata. Java remains the only MySQL writer: it exposes local benchmark persistence APIs over HTTP and owns the new benchmark tables.

**Tech Stack:** Python 3.10, Flask, PyTorch, requests, unittest; Java 17/Spring Boot/MyBatis/MySQL; React, TypeScript, Vite, Vitest, Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-06-local-offload-benchmark-console-design.md`

## Global Constraints

- Bind the benchmark service and browser-facing development server to `127.0.0.1`; never expose MySQL, Chroma, metrics, or the benchmark API publicly.
- Use HTTP polling only; do not introduce WebSocket, Redis, or direct browser-to-AutoDL access.
- Every remote Tensor RPC and remote health request requires `X-Offload-Token`; reject invalid tokens before base64/Tensor decoding and never log tokens, tensors, paths, queries, or image content.
- AutoDL is compute-only. Java owns MySQL and benchmark persistence; Python reaches it through local HTTP APIs and never receives MySQL credentials.
- Preserve the exact resolved immutable execution plan with each run. Permit only one active run; cancellation takes effect after the active sample and completed samples remain retained.
- `REMOTE_ALL` uses `complete_encoders`, not a per-block RPC sequence. Persist warmups for audit but exclude them and unsuccessful durations from aggregates.
- Add canonical DDL to `CLIP_backend/schema.sql` and a forward-only migration under `CLIP_backend/migrations/` for any schema change.
- Use `taskId` where touching established Java/Python contracts; benchmark runs use the separate `runId` name.

## Review Focus

- Remote `/health` must reject a missing or invalid offload token without disclosing configuration or device data.
- Malformed, oversized, or shape-incompatible remote response data must produce a classified failed sample, never raw exception text in the UI or persisted error field.
- An already active or cancelling run must reject a second create request without starting a second worker.
- A cancelled run with completed warmups/measured samples must retain those rows, omit unfinished samples, and end as `CANCELLED`.
- Aggregate percentiles and throughput must use only successful, non-warmup samples; a configuration that misses correctness thresholds must be marked invalid even if faster.

---

### Task 1: Secure and instrument the remote Tensor RPC contract

**Files:**
- Create: `CLIP_model/utils/offload_protocol.py`
- Create: `CLIP_model/tests/test_offload_protocol.py`
- Modify: `CLIP_model/server.py`
- Modify: `CLIP_model/utils/config.py`
- Modify: `CLIP_model/utils/offloader.py`
- Modify: `CLIP_model/model/clip_loader.py`

**Interfaces:**
- Produces: `OffloadRequestMetrics` with `serialize_ms`, `http_rtt_ms`, `deserialize_ms`, request/response byte counts, and server timing fields; `OffloadHandler(..., execution_plan, token, metrics_sink)`.
- Produces: each remote endpoint response `{output, request_id, timings, payload_bytes}` and guarded `/health` response; `ExecutionPlan` consumers can select modules without mutating `OFFLOAD_CONFIG`.

- [ ] **Step 1: Write failing protocol and transport tests**

Create tests that assert a missing/wrong `X-Offload-Token` returns `401` before `decode_tensor` is called, a valid request echoes `request_id` and emits `remote_decode_ms`, `remote_infer_ms`, `remote_encode_ms`, request bytes and response bytes, and an instrumented handler captures serialization/HTTP/deserialization values without retaining payloads or tokens.

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_model.tests.test_offload_protocol -v`

Expected: FAIL because the protected protocol wrapper and instrumented transport do not exist.

- [ ] **Step 3: Implement a reusable guarded RPC wrapper and instrumented handler**

Put header validation, request-id validation, bounded metadata construction, safe Tensor decode/encode helpers, and server timing in `offload_protocol.py`; parameterize `server.py` endpoints through that wrapper. Add `OFFLOAD_TOKEN`, request timeout and maximum payload configuration. Update `OffloadHandler.call_remote()` to attach the token/request id, invoke a metrics sink with timings/sizes only, and return decoded output plus compatibility-safe metadata. Correct `CLIP.forward()` so complete-encoder responses produce the same logits tuple as the local path.

- [ ] **Step 4: Run the protocol and existing model unit tests**

Run: `D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests -v`

Expected: PASS with the new contract tests and existing tests green.

- [ ] **Step 5: Commit the remote contract**

```powershell
git add CLIP_model
git commit -m "feat: secure and instrument offload RPC"
```

### Task 2: Add Java-owned benchmark schema and local persistence API

**Files:**
- Create: `CLIP_backend/migrations/2026-10-06_add_benchmark_runs.sql`
- Modify: `CLIP_backend/schema.sql`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Entity/BenchmarkRun.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Entity/BenchmarkSample.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Dto/benchmark/BenchmarkRunCreateRequest.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Dto/benchmark/BenchmarkRunUpdateRequest.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Dto/benchmark/BenchmarkSampleCreateRequest.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Mapper/BenchmarkMapper.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Service/BenchmarkPersistenceService.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Service/impl/BenchmarkPersistenceServiceImpl.java`
- Create: `CLIP_backend/Management/src/main/java/com/hw/manage/Controller/BenchmarkPersistenceController.java`
- Create: `CLIP_backend/Management/src/test/java/com/hw/manage/Service/impl/BenchmarkPersistenceServiceImplTest.java`

**Interfaces:**
- Consumes: Python requests carrying `runId`, resolved plan JSON, status, bounded summary/error category, and timing/correctness sample fields.
- Produces: loopback-only `POST /benchmark/runs`, `PATCH /benchmark/runs/{runId}`, `POST /benchmark/runs/{runId}/samples`, `GET /benchmark/runs/{runId}`, and paginated `GET /benchmark/runs` APIs; the service alone writes `benchmark_runs`/`benchmark_samples`.

- [ ] **Step 1: Write failing persistence service tests**

Add unit tests that verify run creation stores resolved-plan JSON and input counts, sample insertion rejects a mismatched `runId`, terminal status updates retain prior samples, and list/detail reads return persisted aggregates without raw error text.

- [ ] **Step 2: Run the focused Java test to verify it fails**

Run: `mvn -q -pl Management -am -Dtest=BenchmarkPersistenceServiceImplTest test`

Expected: FAIL because benchmark entities, mapper, service and schema mapping do not exist.

- [ ] **Step 3: Implement canonical tables, migration, mapper, service and local API**

Define `benchmark_runs` and `benchmark_samples` with UUID `run_id`, status, timestamps, resolved configuration JSON, input counts, all specified timing/payload/correctness fields, bounded `error_type`, and indexes on run/sequence/status. Make migration additive and make `schema.sql` include the same tables in dependency-safe drop/create order. Implement transactional Java service validation and MyBatis SQL; keep controller responses scoped to loopback console use and omit tokens, raw paths, queries, tensors and exception messages.

- [ ] **Step 4: Run focused and full backend verification**

Run: `mvn -q test; mvn -q -DskipTests package`

Expected: PASS.

- [ ] **Step 5: Commit Java persistence ownership**

```powershell
git add CLIP_backend
git commit -m "feat: persist benchmark runs through backend"
```

### Task 3: Build the local benchmark domain, runner, and Flask API

**Files:**
- Create: `CLIP_benchmark/benchmark/__init__.py`
- Create: `CLIP_benchmark/benchmark/execution_plan.py`
- Create: `CLIP_benchmark/benchmark/runner.py`
- Create: `CLIP_benchmark/benchmark/aggregation.py`
- Create: `CLIP_benchmark/benchmark/persistence_client.py`
- Create: `CLIP_benchmark/benchmark/app.py`
- Create: `CLIP_benchmark/requirements.txt`
- Create: `CLIP_benchmark/tests/test_execution_plan.py`
- Create: `CLIP_benchmark/tests/test_aggregation.py`
- Create: `CLIP_benchmark/tests/test_runner.py`
- Create: `CLIP_benchmark/tests/test_api.py`

**Interfaces:**
- Consumes: benchmark create body `{mode, advancedOffload?, imagePaths, queries, batchSize, warmupRuns, measuredRuns}`, the model’s `OffloadHandler` metrics sink, and Java benchmark persistence APIs.
- Produces: `ExecutionPlan.resolve()` with all nine flags; serialized run representation and Flask routes `GET /api/health`, `POST /api/experiments`, `GET /api/experiments/{runId}`, `POST /api/experiments/{runId}/cancel`, samples/list/export endpoints from the spec.

- [ ] **Step 1: Write failing domain and API tests**

Cover all four presets, advanced override validation, immutable plan serialization, one-active-run exclusion, cancellation after a sample boundary, remote/token health classification, failed-sample categories, aggregate P50/P95/mean/stdev/throughput/success/correctness rules, and CSV/JSON exports that contain no raw error content.

- [ ] **Step 2: Run the benchmark tests to verify they fail**

Run: `D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_benchmark\tests -v`

Expected: FAIL because the benchmark package and API do not exist.

- [ ] **Step 3: Implement the serial runner and loopback Flask service**

Implement frozen plan/sample/result models; derive `LOCAL_ALL`, `REMOTE_ALL`, `TEXT_LOCAL_IMAGE_REMOTE`, and `TEXT_REMOTE_IMAGE_LOCAL` with complete-encoder behavior for `REMOTE_ALL`. Build a single-worker runner that validates local inputs and protected remote health before creating a worker, persists state through the Java client, runs warmups and measurements serially, computes cosine/Top-K comparisons against the local baseline, and only recognizes cancellation between samples. Bind Flask to `127.0.0.1`, classify failures without response bodies/tracebacks, and implement 1-second-poll-friendly status and export routes.

- [ ] **Step 4: Run the benchmark test suite**

Run: `D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_benchmark\tests -v`

Expected: PASS.

- [ ] **Step 5: Commit the local benchmark service**

```powershell
git add CLIP_benchmark
git commit -m "feat: add local offload benchmark service"
```

### Task 4: Create the local React benchmark console

**Files:**
- Create: `CLIP_benchmark/ui/package.json`
- Create: `CLIP_benchmark/ui/vite.config.ts`
- Create: `CLIP_benchmark/ui/src/main.tsx`
- Create: `CLIP_benchmark/ui/src/App.tsx`
- Create: `CLIP_benchmark/ui/src/api.ts`
- Create: `CLIP_benchmark/ui/src/types.ts`
- Create: `CLIP_benchmark/ui/src/styles.css`
- Create: `CLIP_benchmark/ui/src/components/PlanForm.tsx`
- Create: `CLIP_benchmark/ui/src/components/RunStatus.tsx`
- Create: `CLIP_benchmark/ui/src/components/ComparisonTable.tsx`
- Create: `CLIP_benchmark/ui/src/components/LatencyCharts.tsx`
- Create: `CLIP_benchmark/ui/src/components/RunHistory.tsx`
- Create: `CLIP_benchmark/ui/src/**/*.test.tsx`

**Interfaces:**
- Consumes: only the loopback `/api` benchmark-service routes, never the remote AutoDL host.
- Produces: local console with health, plan form, one-second status polling, cancel/retry controls, aggregate comparison/history, distributions, and CSV/JSON export links.

- [ ] **Step 1: Write failing UI tests**

Test that form validation blocks an empty image/query configuration, preset selection resolves visible advanced toggles, active status renders completed sample count and latest timing, invalid correctness results are visually marked invalid, history/export controls render, and the DOM never renders token/raw-error fields from mocked API data.

- [ ] **Step 2: Run UI tests to verify they fail**

Run: `npm test -- --run`

Working directory: `CLIP_benchmark/ui`

Expected: FAIL because the Vite React project and components do not exist.

- [ ] **Step 3: Implement the dark local laboratory dashboard**

Configure Vite to listen on `127.0.0.1` and proxy only to the local benchmark service. Implement typed API polling with cleanup, preset/advanced-plan controls, health indicators, status/cancellation controls, aggregates and latency distribution visualization, history, and standard browser downloads for the backend-provided exports. Render only safe bounded status/error categories.

- [ ] **Step 4: Run tests and production build**

Run: `npm test -- --run; npm run build`

Working directory: `CLIP_benchmark/ui`

Expected: PASS.

- [ ] **Step 5: Commit the local console**

```powershell
git add CLIP_benchmark/ui
git commit -m "feat: add offload benchmark console"
```

### Task 5: Document secure local operation and run cross-component verification

**Files:**
- Create: `docs/local-offload-benchmark-runbook.md`
- Modify: `CLIP_model/README.md`
- Modify: `CLIP_backend/README.md`
- Create: `CLIP_benchmark/tests/test_contract_integration.py`

**Interfaces:**
- Consumes: the secure model contract, Java persistence contract, local Flask API, and browser setup from Tasks 1–4.
- Produces: reproducible local/AutoDL setup and teardown instructions plus an integration test that uses a fake protected remote service and fake local Java persistence API.

- [ ] **Step 1: Write a failing cross-component contract test**

Add a test that creates one benchmark through the Flask API using a mocked protected remote response with timing data, polls it to a terminal result, verifies persisted warmup/measured sample records and aggregate exclusion rules, and asserts an invalid token generates a classified failed sample.

- [ ] **Step 2: Run the integration test to verify it fails**

Run: `D:\Anaconda\envs\CLIP\python.exe -m unittest CLIP_benchmark.tests.test_contract_integration -v`

Expected: FAIL until all service boundaries and contracts are wired together.

- [ ] **Step 3: Complete wiring and write the runbook**

Resolve only integration defects exposed by the failing test. Document environment variables, loopback bind addresses, token setup (without examples containing real tokens), AutoDL firewall/port expectations, backend migration application, model/server and benchmark/UI launch commands, all four preset methodology, correctness thresholds, export interpretation, and the distinction between remote GPU and end-to-end speedup.

- [ ] **Step 4: Run full verification**

Run: `D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_model\tests -v; D:\Anaconda\envs\CLIP\python.exe -m unittest discover -s CLIP_benchmark\tests -v; mvn -q test; mvn -q -DskipTests package; npm test -- --run; npm run build`

Working directory for final two commands: `CLIP_benchmark/ui`

Expected: PASS.

- [ ] **Step 5: Commit documentation and verification coverage**

```powershell
git add docs CLIP_model CLIP_backend CLIP_benchmark
git commit -m "docs: add offload benchmark runbook"
```

## Plan Self-Review

- Spec coverage: Tasks 1–5 cover the protected remote contract, runtime execution plans, Java-owned persistence, serial runner/cancellation/aggregation, React polling UI, exports, and runbook/E2E contract verification.
- Boundary ruling: the spec’s local persistence interface is realized as local HTTP calls to the Java backend, because `AGENTS.md` makes Java the MySQL owner. This preserves both documents’ constraints.
- Type consistency: `ExecutionPlan` is produced in Task 3 and passed to the Task 1-compatible instrumented `OffloadHandler`; `runId` is used only for benchmark domain records.
- Review focus coverage: protected health (Task 1/3), malformed remote data (Task 1/3), mutual exclusion/cancellation (Task 3), and aggregate correctness (Task 3/5) all have explicit tests.
- Proportion: tasks follow independently testable architecture boundaries without prescribing implementation bodies.


