import unittest

from flask import Flask

from manager.metrics import BackfillMetrics, register_metrics_route


class MetricsEndpointTest(unittest.TestCase):
    def test_metrics_route_is_disabled_or_token_protected(self):
        disabled = Flask(__name__)
        register_metrics_route(disabled, False, "token", BackfillMetrics())
        self.assertEqual(404, disabled.test_client().get("/metrics").status_code)

        enabled = Flask(__name__)
        metrics = BackfillMetrics()
        metrics.record_worker("success", 1, 0.01)
        register_metrics_route(enabled, True, "token", metrics)
        client = enabled.test_client()
        self.assertEqual(401, client.get("/metrics").status_code)
        self.assertEqual(401, client.get("/metrics", headers={"X-Metrics-Token": "wrong"}).status_code)
        response = client.get("/metrics", headers={"X-Metrics-Token": "token"})
        self.assertEqual(200, response.status_code)
        self.assertIn(b"clip_embedding_backfill_jobs_total", response.data)

    def test_empty_metrics_token_never_enables_scraping(self):
        app = Flask(__name__)
        register_metrics_route(app, True, "", BackfillMetrics())
        self.assertEqual(401, app.test_client().get("/metrics").status_code)


if __name__ == "__main__":
    unittest.main()
