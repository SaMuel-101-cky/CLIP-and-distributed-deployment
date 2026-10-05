import requests


def post_task_result(task_id, payload, backend_base_url, callback_token=None, post=requests.post, logger=None):
    url = f"{backend_base_url.rstrip('/')}/ai/tasks/{int(task_id)}/matches"
    headers = {}
    if callback_token:
        headers["X-AI-Callback-Token"] = callback_token
    response = post(url, json=payload, headers=headers, timeout=10)
    response.raise_for_status()
    result = response.json()

    if result.get("code") != 1:
        message = result.get("message") or "backend rejected AI task result"
        raise RuntimeError(message)

    if logger:
        logger.info("[BackgroundTask] AI任务结果已回写后端: task_id=%s", task_id)

    return result
