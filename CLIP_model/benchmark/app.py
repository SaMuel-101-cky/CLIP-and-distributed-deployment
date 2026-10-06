import csv
import io
import threading
import uuid
from pathlib import Path

from flask import Blueprint, Flask, Response, jsonify, request

from .aggregation import summarize
from .clip_executor import ClipSampleExecutor
from .execution_plan import ExecutionPlan


def create_benchmark_blueprint(sample_executor=None, persistence_client=None, image_directory=None):
    benchmark = Blueprint("benchmark", __name__)
    image_directory = Path(
        image_directory or Path(__file__).resolve().parents[2] / "photo_resources" / "test"
    ).resolve()
    runs, lock, active = {}, threading.Lock(), {"run_id": None}
    sample_executor = sample_executor or ClipSampleExecutor()

    def execute(run_id):
        run = runs[run_id]
        plan = run["plan"]
        run["status"] = "RUNNING"
        for index in range(plan.warmup_runs + plan.measured_runs):
            if run["cancel_requested"]:
                run["status"] = "CANCELLED"
                break
            outcome = sample_executor(plan, run["imagePaths"], run["queries"], run["remoteHost"])
            sample = {"sequence_no": index + 1, "warmup": index < plan.warmup_runs, **outcome}
            run["samples"].append(sample)
            if persistence_client:
                persistence_client.record_sample(run_id, sample)
        else:
            run["status"] = "COMPLETED"
        run["summary"] = summarize(run["samples"])
        if persistence_client:
            persistence_client.update_run(run_id, run["status"])
        with lock:
            active["run_id"] = None

    @benchmark.get("/api/health")
    def health():
        return jsonify({"status": "healthy", "bind": "127.0.0.1"})

    @benchmark.get("/api/images")
    def images():
        image_directory.mkdir(parents=True, exist_ok=True)
        allowed = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
        return jsonify({"images": [
            {"name": path.name}
            for path in sorted(image_directory.iterdir())
            if path.is_file() and path.suffix.lower() in allowed
        ]})

    @benchmark.post("/api/experiments")
    def create():
        body = request.get_json(force=True)
        try:
            plan = ExecutionPlan.resolve(
                body["mode"], int(body["batchSize"]), int(body["warmupRuns"]), int(body["measuredRuns"])
            )
        except (KeyError, TypeError, ValueError):
            return jsonify({"error": "invalid_request"}), 400
        with lock:
            if active["run_id"]:
                return jsonify({"error": "experiment_active"}), 409
            run_id = str(uuid.uuid4())
            active["run_id"] = run_id
        image_names, queries = body.get("imageNames", body.get("imagePaths", [])), body.get("queries", [])
        if not isinstance(image_names, list) or not image_names or not isinstance(queries, list) or not queries:
            active["run_id"] = None
            return jsonify({"error": "inputs_required"}), 400
        image_paths = []
        for name in image_names:
            candidate = (image_directory / str(name)).resolve()
            if candidate.parent != image_directory or not candidate.is_file():
                active["run_id"] = None
                return jsonify({"error": "invalid_image_selection"}), 400
            image_paths.append(str(candidate))
        remote_host = body.get("remoteHost")
        if remote_host is not None and (not isinstance(remote_host, str) or len(remote_host) > 255):
            active["run_id"] = None
            return jsonify({"error": "invalid_remote_host"}), 400
        runs[run_id] = {
            "runId": run_id,
            "status": "PENDING",
            "plan": plan,
            "imagePaths": image_paths,
            "queries": queries,
            "remoteHost": remote_host,
            "samples": [],
            "summary": {},
            "cancel_requested": False,
        }
        if persistence_client:
            try:
                persistence_client.create_run(run_id, {
                    "mode": plan.mode,
                    "offload": plan.offload,
                    "batchSize": plan.batch_size,
                    "warmupRuns": plan.warmup_runs,
                    "measuredRuns": plan.measured_runs,
                })
            except Exception:
                active["run_id"] = None
                runs.pop(run_id, None)
                return jsonify({"error": "persistence_unavailable"}), 503
        threading.Thread(target=execute, args=(run_id,), daemon=True).start()
        return jsonify({"runId": run_id}), 202

    @benchmark.get("/api/experiments/<run_id>")
    def get_run(run_id):
        run = runs.get(run_id)
        if not run:
            return jsonify({"error": "not_found"}), 404
        return jsonify({
            "runId": run_id,
            "status": run["status"],
            "sampleCount": len(run["samples"]),
            "summary": run["summary"],
        })

    @benchmark.post("/api/experiments/<run_id>/cancel")
    def cancel(run_id):
        if run_id not in runs:
            return jsonify({"error": "not_found"}), 404
        runs[run_id]["cancel_requested"] = True
        return jsonify({"status": "CANCELLING"}), 202

    @benchmark.get("/api/experiments/<run_id>/export.json")
    def export_json(run_id):
        run = runs.get(run_id)
        if not run:
            return jsonify({"error": "not_found"}), 404
        return jsonify({
            "runId": run_id,
            "status": run["status"],
            "samples": run["samples"],
            "summary": run["summary"],
        })

    @benchmark.get("/api/experiments/<run_id>/export.csv")
    def export_csv(run_id):
        run = runs.get(run_id)
        if not run:
            return jsonify({"error": "not_found"}), 404
        stream = io.StringIO()
        writer = csv.DictWriter(
            stream,
            fieldnames=["sequence_no", "warmup", "success", "total_ms", "error_type"],
            extrasaction="ignore",
        )
        writer.writeheader()
        writer.writerows(run["samples"])
        return Response(stream.getvalue(), mimetype="text/csv")

    return benchmark


def create_app(sample_executor=None, persistence_client=None, image_directory=None):
    app = Flask(__name__)
    app.register_blueprint(create_benchmark_blueprint(sample_executor, persistence_client, image_directory))
    return app
