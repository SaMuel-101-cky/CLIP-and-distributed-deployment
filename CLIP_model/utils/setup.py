import os
import socket
import logging
import sys
import threading
from contextlib import contextmanager
from contextvars import ContextVar
from datetime import datetime
from logging.handlers import RotatingFileHandler
from pathlib import Path
from uuid import uuid4
import torch
from utils import config


_inference_log_path = ContextVar("inference_log_path", default=None)


class _InferenceFileHandler(logging.Handler):
    """Route records to the log file attached to the current inference context."""

    def __init__(self):
        super().__init__(logging.INFO)
        self._handlers = {}
        self._handlers_lock = threading.RLock()

    def emit(self, record):
        log_path = _inference_log_path.get()
        if log_path is None:
            return

        with self._handlers_lock:
            handler = self._handlers.get(log_path)
            if handler is None:
                handler = RotatingFileHandler(
                    log_path,
                    maxBytes=10 * 1024 * 1024,
                    backupCount=5,
                    encoding="utf-8",
                )
                handler.setFormatter(self.formatter)
                handler.setLevel(self.level)
                self._handlers[log_path] = handler
            handler.emit(record)

    def close_log(self, log_path):
        with self._handlers_lock:
            handler = self._handlers.pop(log_path, None)
            if handler is not None:
                handler.close()

    def close(self):
        with self._handlers_lock:
            for handler in self._handlers.values():
                handler.close()
            self._handlers.clear()
        super().close()


def _log_directory(caller_file_path):
    return Path(caller_file_path).resolve().parent / "logs"


@contextmanager
def inference_log_context(logger, caller_file_path, *, now=None, inference_id=None):
    """Write all logger records in this context to one unique inference log."""
    timestamp = now or datetime.now()
    run_id = inference_id or uuid4().hex
    log_dir = _log_directory(caller_file_path)
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"{timestamp:%Y_%m_%d}_{run_id}.log"
    token = _inference_log_path.set(str(log_path))

    logger.info("event=inference.started inference_id=%s", run_id)
    try:
        yield log_path
    finally:
        logger.info("event=inference.completed inference_id=%s", run_id)
        for handler in logger.handlers:
            if isinstance(handler, _InferenceFileHandler):
                handler.close_log(str(log_path))
        _inference_log_path.reset(token)


#获取设备和 IP 信息
def get_device_and_ip():
    if torch.cuda.is_available():
        device = torch.device('cuda:0')  # 显式指定第一块GPU
    elif torch.backends.mps.is_available():
        device = torch.device('mps')  # Apple 硅 GPU
    else:
        device = torch.device('cpu')
    local_ip = socket.gethostbyname(socket.gethostname())
    server_ip = config.SERVER_IP
    return device, local_ip, server_ip

#这里传入了logger，引用对象，仍然返回logger是为了方便链式调用
def configure_logger(logger, caller_file_path, log_filename=None, propagate=True):
    """
    通用日志配置函数

    参数:
        logger: Logger对象
        caller_file_path: 调用者的路径 (__file__)
        log_filename: 保留以兼容现有调用；推理日志始终按日期和推理 ID 命名
        propagate: 是否允许向上传播。
                   建议：如果想看控制台日志，要么设为 True，要么在该函数内手动添加 StreamHandler。
    """
    # 1. 定义统一的格式
    formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(name)s: %(message)s')

    # 2. 添加按推理上下文路由的 FileHandler（防止重复添加）
    if not any(isinstance(handler, _InferenceFileHandler) for handler in logger.handlers):
        file_handler = _InferenceFileHandler()
        file_handler.setFormatter(formatter)
        logger.addHandler(file_handler)

    # 3. 确保控制台有输出 (如果禁止了传播，或者 Logger 本身没有 StreamHandler)
    # 只有当 propagate 为 False 时，我们需要手动保底加一个 StreamHandler，否则控制台就瞎了
    if not propagate:
        has_console = any(isinstance(h, logging.StreamHandler) for h in logger.handlers)
        if not has_console:
            console_handler = logging.StreamHandler(sys.stdout)
            console_handler.setFormatter(formatter)
            console_handler.setLevel(logging.INFO)
            logger.addHandler(console_handler)

    # 4. 设置 Logger 级别
    logger.setLevel(logging.INFO)

    # 5. 设置传播
    logger.propagate = propagate

    return logger
