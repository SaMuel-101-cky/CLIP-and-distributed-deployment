from dataclasses import dataclass


FLAGS = (
    "visual_attn", "visual_mlp", "text_attn", "text_mlp", "vision_conv",
    "vision_proj", "text_proj", "complete_encoders", "cos_sim",
)


@dataclass(frozen=True)
class ExecutionPlan:
    mode: str
    offload: dict[str, bool]
    batch_size: int
    warmup_runs: int
    measured_runs: int

    @classmethod
    def resolve(cls, mode: str, batch_size: int, warmup_runs: int, measured_runs: int):
        if min(batch_size, measured_runs) < 1 or warmup_runs < 0:
            raise ValueError("invalid benchmark counts")
        offload = dict.fromkeys(FLAGS, False)
        if mode == "REMOTE_ALL":
            offload["complete_encoders"] = True
        elif mode == "TEXT_LOCAL_IMAGE_REMOTE":
            for key in ("visual_attn", "visual_mlp", "vision_conv", "vision_proj"):
                offload[key] = True
        elif mode == "TEXT_REMOTE_IMAGE_LOCAL":
            for key in ("text_attn", "text_mlp", "text_proj"):
                offload[key] = True
        elif mode != "LOCAL_ALL":
            raise ValueError("unsupported mode")
        return cls(mode, offload, batch_size, warmup_runs, measured_runs)
