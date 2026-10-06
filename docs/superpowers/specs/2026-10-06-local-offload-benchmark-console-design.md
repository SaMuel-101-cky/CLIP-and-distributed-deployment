# Local CLIP Offload Benchmark Console — Design

## Purpose

Create a local-only experiment console for a course project. It must measure whether model-component offloading from a local CLIP client to a remote AutoDL GPU server is faster than local inference after Tensor serialization and network costs are included.

This is a personal research/testing tool, not a public-facing product. It exists to produce reproducible experimental evidence for the project's report and résumé claims.

## Confirmed deployment boundary

```text
Local machine
├── React benchmark console
├── local Python benchmark service
├── local CLIP client and locally retained CLIP modules
├── Java backend
├── MySQL
├── Chroma
└── local image files
             │ HTTPS / HTTP Tensor RPC
             ▼
AutoDL GPU host
└── Python CLIP server and remote/offloaded CLIP modules
```

MySQL, Chroma, local images, Java-owned task state, and benchmark results remain local. The AutoDL server is a compute-only endpoint: it receives Tensor payloads, runs the requested model module, and returns Tensor payloads and server-side timing fields. It never reads or writes local MySQL or Chroma.

Do not expose local MySQL, Chroma, Java Actuator, model metrics, or the local benchmark service to the public Internet. The remote model service requires an `X-Offload-Token` request header. Model-provider communication stays HTTP; this design does not introduce WebSocket or Redis.

## Existing reusable components

- `CLIP_model/server.py` already exposes remote module endpoints such as `/attention`, `/mlp`, `/vision_conv`, `/encoder_blocks`, and `/complete_encoders`.
- `CLIP_model/utils/offloader.py` already serializes tensors with `torch.save`, Base64-encodes payloads, sends HTTP requests, and deserializes returned tensors.
- `CLIP_model` model classes already use `OffloadHandler` hooks for visual/text attention, MLP, projection, convolution, complete encoder, and similarity operations.

The current `OFFLOAD_*` environment variables are process-start configuration. They are insufficient for an interactive experiment console because an experiment must switch plans without restarting the client. Runtime `ExecutionPlan` objects replace environment mutation for benchmark runs.

## Scope

### In scope

- A local React UI for configuring and viewing experiments.
- A local Python benchmark service, binding only to `127.0.0.1`.
- Runtime selection of local versus remote CLIP modules.
- Four benchmark presets:
  - `LOCAL_ALL`
  - `REMOTE_ALL`
  - `TEXT_LOCAL_IMAGE_REMOTE`
  - `TEXT_REMOTE_IMAGE_LOCAL`
- Optional advanced module toggles for visual/text attention, MLP, projection, convolution, complete encoder, and similarity.
- MySQL persistence for benchmark configurations, samples, and aggregate results.
- CSV and JSON export.
- Correctness validation through embedding cosine similarity and Top-K consistency.
- AutoDL server token validation and structured request/response timing.

### Out of scope

- Public deployment of the console.
- Multi-user accounts, authorization, or public frontend APIs.
- Direct remote access to MySQL or Chroma.
- Redis, WebSocket, vector-database replacement, or autoscaling.
- A general image-management frontend.

## Component design

### React benchmark console

The browser is opened locally, for example at `http://127.0.0.1:<port>`. It never calls AutoDL directly. It calls the local benchmark service using HTTP polling.

Layout:

```text
Header: Local and remote health/device indicators
Left:   mode preset, advanced module toggles, input image set, text queries,
        batch size, warm-up count, and measured-run count
Right:  current status, completed sample count, most recent RTT and remote infer time,
        stop/retry controls
Bottom: distributions and comparison table for total, RTT, remote inference,
        and serialization; history plus CSV/JSON export
```

The UI uses a dark laboratory/dashboard visual direction. It displays no secrets, raw Tensor content, or full error payloads.

### Local benchmark service

The service owns experiment execution, serializes all runs, and persists results locally. It reuses the local CLIP client and builds an `OffloadHandler` from an immutable `ExecutionPlan` per experiment.

Only one experiment may run at a time. This prevents GPU, CPU, and network contention from corrupting comparisons. Cancellation becomes effective after the active sample completes; already-completed samples are retained.

Proposed HTTP API:

```text
GET  /api/health
POST /api/experiments
GET  /api/experiments/{runId}
POST /api/experiments/{runId}/cancel
GET  /api/experiments/{runId}/samples
GET  /api/experiments/{runId}/export.csv
GET  /api/experiments/{runId}/export.json
GET  /api/experiments?limit=&offset=
```

The browser polls the run resource once per second. No WebSocket is used.

### ExecutionPlan

Each plan is immutable and is stored with the run. It includes the preset name and resolved flags, not only the preset name.

```json
{
  "mode": "TEXT_LOCAL_IMAGE_REMOTE",
  "offload": {
    "visualAttn": true,
    "visualMlp": true,
    "textAttn": false,
    "textMlp": false,
    "visionConv": true,
    "visionProjection": true,
    "textProjection": false,
    "completeEncoders": false,
    "cosSim": false
  },
  "batchSize": 4,
  "warmupRuns": 5,
  "measuredRuns": 30,
  "imageSet": "selected-local-images",
  "queries": ["a cat", "a dog"]
}
```

`REMOTE_ALL` uses the existing complete-encoder endpoint rather than making every block an independent network call. This keeps the full-remote condition representative rather than intentionally dominated by repeated RPC overhead.

## Remote server contract

Every offload endpoint requires `X-Offload-Token`. A missing or invalid token is rejected before Tensor decoding.

Request payloads continue to contain the existing serialized Tensor data plus a client-generated `request_id`. The response includes the existing output plus bounded timing and size metadata:

```json
{
  "output": "<base64 tensor>",
  "request_id": "uuid",
  "timings": {
    "remote_decode_ms": 0,
    "remote_infer_ms": 0,
    "remote_encode_ms": 0
  },
  "payload_bytes": {
    "request": 0,
    "response": 0
  }
}
```

The remote server never logs raw tensors, tokens, paths, query text, or image content.

## Timing methodology

Each sample records:

| Field | Meaning |
| --- | --- |
| `total_ms` | local request entry to final result completion |
| `local_preprocess_ms` | local image/text preprocessing |
| `serialize_ms` | Tensor serialization and Base64 encoding |
| `http_rtt_ms` | local observed remote HTTP round trip |
| `remote_decode_ms` | remote Tensor decoding and device transfer work |
| `remote_infer_ms` | remote module inference |
| `remote_encode_ms` | remote result serialization |
| `deserialize_ms` | local response decoding |
| `local_postprocess_ms` | local normalization/retrieval postprocessing |
| `request_bytes` / `response_bytes` | transmitted payload sizes |
| `success` / `error_type` | result status without raw exception text |
| `embedding_cosine_similarity` | numerical consistency check |
| `top_k_consistent` | retrieval consistency check |

Do not derive one-way network latency by subtracting clocks across machines. The reproducible network-plus-queue approximation is:

```text
network_and_queue_ms = http_rtt_ms
  - remote_decode_ms
  - remote_infer_ms
  - remote_encode_ms
```

Warm-up samples are retained for audit but excluded from aggregate statistics. Measured samples produce mean, median/P50, P95, standard deviation, throughput, success rate, and correctness pass rate. A faster configuration that fails the correctness thresholds is reported as invalid, not as a winning result.

## Persistence

Add an explicit schema migration and update `CLIP_backend/schema.sql` for local benchmark ownership.

`benchmark_runs` stores:

- primary key, status, created/started/completed timestamps;
- resolved mode and configuration JSON;
- input dataset identity and image/query counts;
- warm-up and measured-run counts;
- aggregate statistics and correctness summary.

`benchmark_samples` stores:

- primary key and `run_id` foreign key;
- sample sequence and warm-up flag;
- all timing, payload-size, success, and correctness fields;
- bounded error category only.

The local benchmark service writes these tables through an explicit local persistence interface. AutoDL has no database credential and never accesses these tables.

## Run workflow

1. UI loads local and remote health/device status.
2. User selects a preset or advanced execution plan and benchmark parameters.
3. Service validates images, plan compatibility, the local model, and remote token-protected health endpoint.
4. Service creates a pending run and executes warm-up samples.
5. Service executes measured samples serially and persists each result.
6. Service computes aggregates and correctness checks, then marks the run terminal.
7. UI shows distributions and mode comparisons; user exports CSV or JSON for the report.

## Failure handling

- Remote unavailable, timeout, rejected token, invalid Tensor shape, and remote 5xx responses become classified failed samples.
- A run can complete with partial failures; aggregate output includes failure rate and excludes failed duration samples from percentile calculations.
- Service startup validation failure creates no running worker.
- A cancelled run is marked `CANCELLED` after the active sample returns.
- The runner preserves the exact resolved plan for every completed or failed run.

## Verification strategy

- Python unit tests: preset-to-plan mapping, timing aggregation, percentile calculation, cancellation, failed-sample classification, result exports, and token enforcement.
- Flask API tests: create/poll/cancel/export APIs and single-run mutual exclusion.
- React tests: validation, status rendering, comparison table, and no-secret rendering.
- Contract tests: mock remote server responses with timing metadata and malformed Tensor/error cases.
- End-to-end tests: all four presets against the real AutoDL server, with the same local inputs, batch size, warm-up count, and repeat count.

## Experimental reporting requirements

For each preset, report identical inputs and execution settings, sample count, mean/P50/P95 total latency, local versus remote compute time, network-plus-queue cost, payload sizes, throughput, success rate, and correctness pass rate. Include a table and distribution chart. The result statement must distinguish a remote GPU speedup from an end-to-end speedup after transport overhead.
