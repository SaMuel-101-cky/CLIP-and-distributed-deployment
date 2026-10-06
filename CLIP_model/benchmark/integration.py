import threading

from .app import create_benchmark_blueprint
from .clip_executor import ClipSampleExecutor


def register_benchmark_routes(
    app,
    model,
    offloader,
    predict_fn,
    default_remote_host,
    execution_lock=None,
    image_directory=None,
):
    """Attach benchmark routes to the running local model service."""
    executor = ClipSampleExecutor(
        model=model,
        offloader=offloader,
        predict_fn=predict_fn,
        default_remote_host=default_remote_host,
        execution_lock=execution_lock or threading.RLock(),
    )
    app.register_blueprint(
        create_benchmark_blueprint(sample_executor=executor, image_directory=image_directory)
    )
    return executor
