from pathlib import Path
from time import perf_counter
import sys


class ClipSampleExecutor:
    """Lazily constructs a plan-specific CLIP callable for one benchmark sample."""
    def __init__(self, factory=None):
        self.factory = factory or self._build_real_callable

    def __call__(self, plan, image_paths, queries, remote_host=None):
        started = perf_counter()
        try:
            infer = self.factory(plan, remote_host)
            infer(image_paths, queries)
            return {"success": True, "total_ms": (perf_counter() - started) * 1000, "image_paths": list(image_paths)}
        except (FileNotFoundError, ValueError):
            return {"success": False, "total_ms": (perf_counter() - started) * 1000, "error_type": "INVALID_INPUT"}
        except TimeoutError:
            return {"success": False, "total_ms": (perf_counter() - started) * 1000, "error_type": "REMOTE_TIMEOUT"}
        except Exception:
            return {"success": False, "total_ms": (perf_counter() - started) * 1000, "error_type": "REMOTE_UNAVAILABLE"}

    def _build_real_callable(self, plan, remote_host=None):
        model_root = Path(__file__).resolve().parents[2] / "CLIP_model"
        sys.path.insert(0, str(model_root))
        from model.clip_loader import build_model
        from utils.offloader import OffloadHandler
        from utils.pred import predict
        from utils import config
        import torch
        state = torch.jit.load(str(model_root / "ViT-L-14.pt"), map_location="cpu").state_dict()
        handler = OffloadHandler(remote_host or config.SERVER_IP, config.SERVER_PORT, config.OFFLOAD_CONFIG,
                                 token=config.OFFLOAD_TOKEN, execution_plan=plan)
        model = build_model(state, offload_handler=handler).to("cuda" if torch.cuda.is_available() else "cpu").eval()
        return lambda images, queries: predict(model, images, queries)
