import hmac

from flask import Response
from prometheus_client import CollectorRegistry, Counter, Histogram, CONTENT_TYPE_LATEST, generate_latest


class BackfillMetrics:
    def __init__(self, registry=None):
        self.registry = registry or CollectorRegistry()
        self.jobs = Counter("clip_embedding_backfill_jobs_total", "Embedding backfill worker outcomes", ["outcome"], registry=self.registry)
        self.duration = Histogram("clip_embedding_backfill_duration_seconds", "Embedding backfill worker duration", ["outcome"], registry=self.registry)
        self.photos = Counter("clip_embedding_backfill_photos_total", "Embedding backfill attempted photos", ["outcome"], registry=self.registry)
        self.callback_failures = Counter("clip_embedding_callback_failures_total", "Embedding callback post failures", ["outcome"], registry=self.registry)

    def record_worker(self, outcome, photo_count, duration_seconds):
        self.jobs.labels(outcome=outcome).inc()
        self.duration.labels(outcome=outcome).observe(duration_seconds)
        self.photos.labels(outcome=outcome).inc(photo_count)

    def record_callback_failure(self):
        self.callback_failures.labels(outcome="exception").inc()


def register_metrics_route(app, enabled, token, metrics):
    @app.get("/metrics")
    def metrics_endpoint():
        if not enabled:
            return "", 404
        from flask import request
        provided = request.headers.get("X-Metrics-Token", "")
        if not token or not hmac.compare_digest(token, provided):
            return "", 401
        return Response(generate_latest(metrics.registry), content_type=CONTENT_TYPE_LATEST)

    return metrics_endpoint
