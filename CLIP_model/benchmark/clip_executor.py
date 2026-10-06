from pathlib import Path
from time import perf_counter
import sys
import threading


class ClipSampleExecutor:
    """Runs a sample against the already-loaded client model when available."""

    def __init__(
        self,
        model=None,
        offloader=None,
        predict_fn=None,
        default_remote_host=None,
        execution_lock=None,
        factory=None,
    ):
        self.model = model
        self.offloader = offloader
        self.predict_fn = predict_fn
        self.default_remote_host = default_remote_host
        self.execution_lock = execution_lock or threading.RLock()
        self.factory = factory or (None if model is not None else self._build_real_callable)

    def __call__(self, plan, image_paths, queries, remote_host=None):
        started = perf_counter()
        try:
            if self.factory:
                self.factory(plan, remote_host)(image_paths, queries)
            else:
                self._run_with_client_model(plan, image_paths, queries, remote_host)
            return {
                "success": True,
                "total_ms": (perf_counter() - started) * 1000,
                "image_paths": list(image_paths),
            }
        except (FileNotFoundError, ValueError):
            return {"success": False, "total_ms": (perf_counter() - started) * 1000, "error_type": "INVALID_INPUT"}
        except TimeoutError:
            return {"success": False, "total_ms": (perf_counter() - started) * 1000, "error_type": "REMOTE_TIMEOUT"}
        except Exception:
            return {"success": False, "total_ms": (perf_counter() - started) * 1000, "error_type": "REMOTE_UNAVAILABLE"}

    def _run_with_client_model(self, plan, image_paths, queries, remote_host):
        if self.model is None or self.offloader is None or self.predict_fn is None:
            raise RuntimeError("benchmark executor is missing the client model dependencies")
        with self.execution_lock:
            original_host = self.offloader.server_ip
            original_config = self.offloader.config
            try:
                self.offloader.server_ip = remote_host or self.default_remote_host
                self.offloader.config = dict(plan.offload)
                self.predict_fn(self.model, image_paths, queries)
            finally:
                self.offloader.server_ip = original_host
                self.offloader.config = original_config

    def _build_real_callable(self, plan, remote_host=None):
        model_root = Path(__file__).resolve().parents[1]
        sys.path.insert(0, str(model_root))
        from model.clip_loader import build_model
        from utils import config
        from utils.offloader import OffloadHandler
        from utils.pred import predict
        import torch

        state = torch.jit.load(str(model_root / "ViT-L-14.pt"), map_location="cpu").state_dict()
        handler = OffloadHandler(
            remote_host or config.SERVER_IP,
            config.SERVER_PORT,
            config.OFFLOAD_CONFIG,
            token=config.OFFLOAD_TOKEN,
            execution_plan=plan,
        )
        model = build_model(state, offload_handler=handler).to("cuda" if torch.cuda.is_available() else "cpu").eval()
        return lambda images, text_queries: predict(model, images, text_queries)
