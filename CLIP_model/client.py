import sys

import logging
import threading
from flask import Flask, request, jsonify
import torch
from utils.offloader import OffloadHandler
from model.clip_loader import build_model
from utils.setup import get_device_and_ip,configure_logger
from utils.pred import predict
from utils import config as cfg
from manager.ai_task_payload import (
    build_category_matches,
    build_smoke_matches,
    build_task_result_payload,
)
from manager.backend_client import post_embedding_result, post_task_result
from manager.embedding_backfill import run_embedding_backfill_task
from manager.embedding_backfill_payload import validate_backfill_request
from manager.metrics import BackfillMetrics, register_metrics_route
from manager.vector_search import build_text_search_result_with_vector_store
from manager.vector_store import ChromaVectorStore, VectorStoreConfig
from utils.embedding import encode_images, encode_texts
from benchmark.integration import register_benchmark_routes


sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

app = Flask(__name__)
backfill_metrics = BackfillMetrics()

DEVICE, local_ip, server_ip = get_device_and_ip()

logger = logging.getLogger('client')
logger = configure_logger(logger, __file__, propagate=False)
register_metrics_route(app, cfg.METRICS_ENABLED, cfg.METRICS_TOKEN, backfill_metrics)


def build_vector_store_from_config(config_module, logger=None):
    if not config_module.VECTOR_STORE_ENABLED:
        return None

    vector_config = VectorStoreConfig(
        enabled=config_module.VECTOR_STORE_ENABLED,
        persist_dir=config_module.CHROMA_PERSIST_DIR,
        collection_name=config_module.CHROMA_COLLECTION,
        embedding_model=config_module.EMBEDDING_MODEL_NAME,
        fallback_enabled=config_module.VECTOR_SEARCH_FALLBACK,
    )
    try:
        return ChromaVectorStore(vector_config)
    except Exception as exc:
        if logger:
            logger.exception("[VectorStore] 初始化 Chroma 失败，将使用暴力匹配 fallback: %s", exc)
        return None


def try_upsert_image_embeddings(vector_store, user_id, photo_id_list, photo_list, logger=None):
    if vector_store is None:
        return
    if user_id is None:
        if logger:
            logger.warning("[VectorStore] 缺少 userId，跳过图片向量写入")
        return

    try:
        with model_execution_lock:
            image_embeddings = encode_images(model, photo_list)
        vector_store.upsert_image_embeddings(user_id, photo_id_list, photo_list, image_embeddings)
        if logger:
            logger.info("[VectorStore] 图片向量已写入 Chroma: user_id=%s count=%s", user_id, len(photo_id_list))
    except Exception as exc:
        if logger:
            logger.exception("[VectorStore] 图片向量写入失败，分类任务继续执行: %s", exc)


state_dict = torch.jit.load('ViT-L-14.pt', map_location='cpu').state_dict()

offloader = OffloadHandler(
    server_ip=cfg.SERVER_IP,
    server_port=cfg.SERVER_PORT,
    config=cfg.OFFLOAD_CONFIG,
    logger=logger,
    token=cfg.OFFLOAD_TOKEN,
    request_timeout_seconds=cfg.OFFLOAD_REQUEST_TIMEOUT_SECONDS,
)

model = build_model(state_dict, offload_handler=offloader).to(DEVICE).eval()
vector_store = build_vector_store_from_config(cfg, logger)
model_execution_lock = threading.RLock()
register_benchmark_routes(
    app=app,
    model=model,
    offloader=offloader,
    predict_fn=predict,
    default_remote_host=cfg.SERVER_IP,
    execution_lock=model_execution_lock,
)

print("模型加载完成")

@app.route('/health', methods=['GET'])            #客户端测试服务器健康状态的接口
def health_check():
    return jsonify({
        'status': 'healthy',
        'device': str(DEVICE)
    })

@app.route("/predict", methods=["POST"])
def predict_():
    data = request.get_json(force=True)

    image_urls = data.get("image_urls", None)
    class_names = data.get("class_names", None)

    if not image_urls or not isinstance(image_urls, list):
        return jsonify({"error": "image_urls 必须是非空列表"}), 400

    if not class_names or not isinstance(class_names, list):
        return jsonify({"error": "class_names 必须是非空列表"}), 400

    try:
        with model_execution_lock:
            logits_per_image, _ = predict(model, image_urls, class_names)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

    # logits => 概率
    probs = torch.softmax(logits_per_image, dim=-1)  # [B, N]
    probs_np = probs.cpu().numpy()

    predictions = []
    for i, url in enumerate(image_urls):
        top1_idx = int(probs_np[i].argmax())
        predictions.append({
            "url": url,
            "class": class_names[top1_idx]
        })

    return jsonify({"results": predictions})

def func1_process(
    task_id,
    user_id,
    photo_list,
    photo_id_list,
    description_list,
    description_id_list,
    vector_store,
    logger
 ):

    try:
        if cfg.CLIP_SMOKE_MODE:
            matches = [
                build_smoke_matches([photo_id], description_id_list[0], "CATEGORY", top_k=1)[0]
                for photo_id in photo_id_list
            ]
        else:
            try_upsert_image_embeddings(vector_store, user_id, photo_id_list, photo_list, logger)
            with model_execution_lock:
                logits_per_image, _ = predict(model, photo_list, description_list)
            probs = torch.softmax(logits_per_image, dim=-1)
            probs_np = probs.cpu().numpy()
            matches = build_category_matches(probs_np, photo_id_list, description_id_list)
        payload = build_task_result_payload(task_id, "SUCCESS", matches)
        post_task_result(task_id, payload, cfg.BACKEND_BASE_URL, cfg.AI_CALLBACK_TOKEN, logger=logger)
    except Exception as e:
        logger.exception("[BackgroundTask] 分类任务处理失败: task_id=%s", task_id)
        payload = build_task_result_payload(task_id, "FAILED", [], str(e))
        post_task_result(task_id, payload, cfg.BACKEND_BASE_URL, cfg.AI_CALLBACK_TOKEN, logger=logger)


def func_embedding_backfill_process(task_id, request_data, vector_store, logger):
    with model_execution_lock:
        return run_embedding_backfill_task(
            task_id, request_data, vector_store, model, encode_images,
            cfg.BACKEND_BASE_URL, cfg.AI_CALLBACK_TOKEN, post_embedding_result,
            smoke_mode=cfg.CLIP_SMOKE_MODE, logger=logger, metrics=backfill_metrics,
        )


@app.route("/embeddings/backfill", methods=["POST"])
def receive_embedding_backfill_task():
    try:
        data = validate_backfill_request(request.get_json(force=True))
    except Exception as error:
        return jsonify({"code": 0, "message": str(error)}), 400
    try:
        threading.Thread(target=func_embedding_backfill_process,
                         args=(data["taskId"], data, vector_store, logger)).start()
        logger.info("event=embedding_backfill.accepted task_id=%s user_id=%s photo_count=%s",
                    data["taskId"], data["userId"], len(data["photosId"]))
        return jsonify({"code": 1, "message": "embedding backfill task accepted"}), 200
    except Exception as error:
        logger.exception("Unable to start embedding backfill task")
        return jsonify({"code": 0, "message": str(error)}), 500



@app.route("/upload", methods=["POST"])
def receive_category_task():
    #读取json
    try:
        data = request.get_json(force=True)
        photo_list = data.get("photosList")
        photo_id_list = data.get("photosId")
        description_list = data.get("descriptionsList")
        description_id_list = data.get("descriptionsId")
        task_id = data.get("taskId")
        user_id = data.get("userId")

        if not (photo_list and photo_id_list and description_list and description_id_list) or task_id is None:
            return jsonify(
                {"code": 0, "message": "参数不完整：photosList, photosId, descriptionsList, descriptionsId, taskId 均为必须"}), 400
        if len(description_list) != len(description_id_list):
            return jsonify(
                {"code": 0, "message": "描述文本列表和描述ID列表长度不一致"}), 400
        if len(photo_list) != len(photo_id_list):
            return jsonify(
                {"code": 0, "message": "图片地址列表和图片ID列表长度不一致"}), 400

    except Exception as e:
        app.logger.error(f"参数解析错误: {e}")
        return jsonify({"code": 0, "message": "请求数据格式错误"}), 400

    try:
        thread = threading.Thread(
            target = func1_process,
            args=(task_id, user_id, photo_list, photo_id_list, description_list, description_id_list, vector_store, logger)
        )
        thread.start()

        return jsonify({"code": 1, "message": "图片上传及匹配任务已接收，正在后台处理"}), 200

    except Exception as e:
        app.logger.error(f"启动后台线程失败: {e}")
        return jsonify({"code": 0, "message": f"启动后台任务失败：{str(e)}"}), 500


def func2_process(
        task_id,
        user_id,
        photo_list,
        photo_id_list,
        description,
        description_id,
        vector_store,
        logger
):
    try:
        if cfg.CLIP_SMOKE_MODE:
            matches = build_smoke_matches(
                photo_id_list,
                description_id,
                "TEXT_SEARCH",
                top_k=cfg.TEXT_SEARCH_TOP_K,
            )
        else:
            with model_execution_lock:
                search_result = build_text_search_result_with_vector_store(
                    vector_store=vector_store,
                    model=model,
                    user_id=user_id,
                    description=description,
                    description_id=description_id,
                    photo_list=photo_list,
                    photo_id_list=photo_id_list,
                    top_k=cfg.TEXT_SEARCH_TOP_K,
                    fallback_enabled=cfg.VECTOR_SEARCH_FALLBACK,
                    encode_texts_fn=encode_texts,
                    predict_fn=predict,
                )
            matches = search_result.matches
            if search_result.source == "CHROMA":
                logger.info(
                    "[VectorStore] 文本搜图命中 Chroma: task_id=%s user_id=%s hits=%s matches=%s",
                    task_id,
                    user_id,
                    search_result.vector_hit_count,
                    len(matches),
                )
            else:
                logger.info(
                    "[VectorStore] 文本搜图使用 fallback: task_id=%s user_id=%s source=%s reason=%s vector_hits=%s matches=%s",
                    task_id,
                    user_id,
                    search_result.source,
                    search_result.fallback_reason,
                    search_result.vector_hit_count,
                    len(matches),
                )
        payload = build_task_result_payload(task_id, "SUCCESS", matches)
        post_task_result(task_id, payload, cfg.BACKEND_BASE_URL, cfg.AI_CALLBACK_TOKEN, logger=logger)
    except Exception as e:
        logger.exception("[BackgroundTask] 文本搜图任务处理失败: task_id=%s", task_id)
        payload = build_task_result_payload(task_id, "FAILED", [], str(e))
        post_task_result(task_id, payload, cfg.BACKEND_BASE_URL, cfg.AI_CALLBACK_TOKEN, logger=logger)

#以文搜图
@app.route("/getPhotos", methods=["POST"])
def search_photo_by_description():
    try:
        data = request.get_json(force=True)
        task_id = data.get("taskId", None)
        description = data.get("description", None)
        description_id = data.get("descriptionId", None)
        user_id = data.get("userId", None)
        photo_list = data.get("photosList", None)
        photo_id_list = data.get("photosId", None)

        if task_id is None or user_id is None or description is None or description_id is None or not photo_list or not photo_id_list:
            return jsonify(
                {"code": 0, "message": "参数不完整：taskId, userId, description, descriptionId, photosList, photosId 均为必须"}), 400
        if len(photo_list) != len(photo_id_list):
            return jsonify(
                {"code": 0, "message": "图片路径列表和图片ID列表长度不一致"}), 400

    except Exception as e:
        app.logger.error(f"参数解析错误: {e}")
        return jsonify({"code": 0, "message": "请求数据格式错误"}), 400
    thread = threading.Thread(
        target = func2_process,
        args = (task_id, user_id, photo_list, photo_id_list, description, description_id, vector_store, logger)
    )
    thread.start()

        # 立即返回响应给客户端
    return jsonify({"code": 1, "message": "任务已接收，正在后台进行推理和匹配，请稍后查询结果"}), 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=cfg.SERVER_PORT)
