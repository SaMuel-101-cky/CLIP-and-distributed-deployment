"""Safe, transport-only helpers for the remote CLIP offload contract."""

from __future__ import annotations

from dataclasses import dataclass
from time import perf_counter
from typing import Any, Callable, Mapping
from uuid import UUID


@dataclass(frozen=True)
class RpcResult:
    status_code: int
    body: dict[str, Any]


def _milliseconds(start: float) -> float:
    return round((perf_counter() - start) * 1000, 3)


def _request_id(payload: Mapping[str, Any]) -> str | None:
    value = payload.get("request_id")
    if not isinstance(value, str):
        return None
    try:
        UUID(value)
    except ValueError:
        # Existing callers may use a correlation value that is not a UUID.
        # Preserve it only when it is short, printable data.
        if not value or len(value) > 128 or not value.isprintable():
            return None
    return value


def execute_tensor_rpc(
    *,
    headers: Mapping[str, str],
    payload: Mapping[str, Any],
    expected_token: str,
    decode: Callable[[str], Any],
    infer: Callable[[Any], Any],
    encode: Callable[[Any], str],
) -> RpcResult:
    """Authenticate, decode, execute and encode without exposing request data."""
    if not expected_token or headers.get("X-Offload-Token") != expected_token:
        return RpcResult(401, {"error": "unauthorized"})

    data = payload.get("data")
    request_id = _request_id(payload)
    if not isinstance(data, str) or request_id is None:
        return RpcResult(400, {"error": "invalid_request"})

    try:
        decode_started = perf_counter()
        decoded = decode(data)
        decode_ms = _milliseconds(decode_started)

        infer_started = perf_counter()
        output = infer(decoded)
        infer_ms = _milliseconds(infer_started)

        encode_started = perf_counter()
        output_data = encode(output)
        encode_ms = _milliseconds(encode_started)
    except (TypeError, ValueError, KeyError, RuntimeError):
        return RpcResult(400, {"request_id": request_id, "error": "invalid_tensor"})

    return RpcResult(
        200,
        {
            "output": output_data,
            "request_id": request_id,
            "timings": {
                "remote_decode_ms": decode_ms,
                "remote_infer_ms": infer_ms,
                "remote_encode_ms": encode_ms,
            },
            "payload_bytes": {
                "request": len(data.encode("utf-8")),
                "response": len(output_data.encode("utf-8")),
            },
        },
    )
