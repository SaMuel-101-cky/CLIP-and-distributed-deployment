# utils/offloader.py
import torch
import io
import base64
import requests
import time
import logging
from dataclasses import dataclass
from typing import Callable, Optional
from uuid import uuid4


@dataclass(frozen=True)
class OffloadRequestMetrics:
    endpoint: str
    serialize_ms: float
    http_rtt_ms: float
    deserialize_ms: float
    request_bytes: int
    response_bytes: int
    remote_decode_ms: float | None
    remote_infer_ms: float | None
    remote_encode_ms: float | None


class OffloadHandler:
    def __init__(self, server_ip, server_port, config, logger=None, token="", metrics_sink: Optional[Callable[[OffloadRequestMetrics], None]] = None, request_timeout_seconds=30, execution_plan=None):
        self.server_ip = server_ip
        self.server_port = server_port
        self.config = getattr(execution_plan, "offload", config)
        self.logger = logger or logging.getLogger('client')
        self.token = token
        self.metrics_sink = metrics_sink
        self.request_timeout_seconds = request_timeout_seconds

    def should_offload(self, module_type: str) -> bool:
        """
        判断当前模块是否需要卸载到服务器。

        参数:
            module_type: 模块标识，对应 OFFLOAD_CONFIG 中的 key，
                        如 'visual_attn' / 'text_mlp' / 'vision_conv' / 'complete_encoders' 等

        TODO: 后续可扩展为逐层粒度的卸载控制。
              方案思路：
              1. 增加 layer_id: int 和 encoder_type: str 参数
              2. 拼接 key = f"{encoder_type}_{module_type}_{layer_id}"
                 （如 visual_attn_5 表示只卸载视觉编码器第5层attention）
              3. 在 OFFLOAD_CONFIG 中增加 per-layer 环境变量
                 （如 OFFLOAD_VISUAL_ATTN_5）
              4. 查询时优先查细粒度 key，未命中则回落粗粒度 key
              当前设计为模块级粒度（如 visual_attn 整体卸载）。
        """
        return self.config.get(module_type, False)

    def call_remote(self, endpoint: str, data_dict: dict, device: torch.device, fallback_fn=None):
        """
        序列化数据 -> 发送请求 -> 反序列化结果
        如果远程调用失败且提供了 fallback_fn，则降级执行本地计算。
        """
        try:
            # 1. 序列化
            serialize_started = time.perf_counter()
            buffer = io.BytesIO()
            # 将 tensor 转为 cpu 以便序列化
            cpu_data = {k: v.cpu() if isinstance(v, torch.Tensor) else v for k, v in data_dict.items()}
            torch.save(cpu_data, buffer)
            data_str = base64.b64encode(buffer.getvalue()).decode()
            serialize_ms = (time.perf_counter() - serialize_started) * 1000

            # 2. 发送请求
            url = f"http://{self.server_ip}:{self.server_port}/{endpoint}"
            payload = {
                "data": data_str,
                "request_id": str(uuid4()),
                "client_send_ts": time.time(),
            }

            t_start = time.perf_counter()
            headers = {"X-Offload-Token": self.token} if self.token else {}
            resp = requests.post(url, json=payload, headers=headers, timeout=self.request_timeout_seconds)
            resp.raise_for_status()
            resp_json = resp.json()
            http_ms = (time.perf_counter() - t_start) * 1000

            if self.logger:
                self.logger.info(f"[{endpoint}] server={self.server_ip} rtt={http_ms:.2f}ms type=传输")

            # 3. 反序列化
            deserialize_started = time.perf_counter()
            output_str = resp_json['output']
            output_buffer = io.BytesIO(base64.b64decode(output_str))
            output_dict = torch.load(output_buffer, weights_only=True)
            if "output" in output_dict:
                output = output_dict["output"].to(device)
            else:
                output = {
                    key: value.to(device) if isinstance(value, torch.Tensor) else value
                    for key, value in output_dict.items()
                }
            deserialize_ms = (time.perf_counter() - deserialize_started) * 1000
            if self.metrics_sink:
                timings = resp_json.get("timings", {})
                sizes = resp_json.get("payload_bytes", {})
                self.metrics_sink(OffloadRequestMetrics(
                    endpoint=endpoint,
                    serialize_ms=serialize_ms,
                    http_rtt_ms=http_ms,
                    deserialize_ms=deserialize_ms,
                    request_bytes=int(sizes.get("request", len(data_str.encode("utf-8")))),
                    response_bytes=int(sizes.get("response", len(output_str.encode("utf-8")))),
                    remote_decode_ms=timings.get("remote_decode_ms"),
                    remote_infer_ms=timings.get("remote_infer_ms"),
                    remote_encode_ms=timings.get("remote_encode_ms"),
                ))
            return output

        except Exception as e:
            if fallback_fn is not None:
                if self.logger:
                    self.logger.warning(
                        "[%s] 远程调用失败，降级本地计算: %s", endpoint, e
                    )
                return fallback_fn()
            if self.logger:
                self.logger.error(f"[{endpoint}] Failed: {e}")
            raise
