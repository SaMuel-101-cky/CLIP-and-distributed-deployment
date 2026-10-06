import os
from dotenv import load_dotenv

load_dotenv()

SERVER_IP = os.getenv("SERVER_IP")
SERVER_PORT = int(os.getenv("SERVER_PORT", 5000))
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")
AI_CALLBACK_TOKEN = os.getenv("AI_CALLBACK_TOKEN")
TEXT_SEARCH_TOP_K = int(os.getenv("TEXT_SEARCH_TOP_K", 5))
CLIP_SMOKE_MODE = os.getenv("CLIP_SMOKE_MODE", "false").lower() == "true"
VECTOR_STORE_ENABLED = os.getenv("VECTOR_STORE_ENABLED", "true").lower() == "true"
CHROMA_PERSIST_DIR = os.getenv("CHROMA_PERSIST_DIR", "./chroma_data")
CHROMA_COLLECTION = os.getenv("CHROMA_COLLECTION", "clip_image_embeddings")
EMBEDDING_MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "clip-vit-l-14")
VECTOR_SEARCH_FALLBACK = os.getenv("VECTOR_SEARCH_FALLBACK", "true").lower() == "true"
METRICS_ENABLED = os.getenv("METRICS_ENABLED", "false").lower() == "true"
METRICS_TOKEN = os.getenv("METRICS_TOKEN", "")


# TODO: 后续可扩展为逐层粒度的卸载控制。
#       当前为模块级粒度（如 visual_attn 控制所有24层视觉attention的卸载）。
#       若需要精确控制到某层（如只卸载 visual_attn_5），可增加 per-layer 环境变量：
#         OFFLOAD_VISUAL_ATTN_5=true
#         OFFLOAD_TEXT_MLP_0=true
#       并在 OffloadHandler.should_offload() 中拼接细粒度 key 进行查询。
OFFLOAD_CONFIG = {
    'visual_attn': os.getenv("OFFLOAD_VISUAL_ATTN", 'false').lower() == 'true',
    'visual_mlp': os.getenv("OFFLOAD_VISUAL_MLP", 'false').lower() == 'true',
    'text_attn': os.getenv("OFFLOAD_TEXT_ATTN", 'false').lower() == 'true',
    'text_mlp': os.getenv("OFFLOAD_TEXT_MLP", 'false').lower() == 'true',

    'visual_encoder': os.getenv("OFFLOAD_VISUAL_ENCODER", 'false').lower() == 'true',
    'text_encoder': os.getenv("OFFLOAD_TEXT_ENCODER", 'false').lower() == 'true',

    'vision_conv': os.getenv("OFFLOAD_VISUAL_CONV", 'false').lower() == 'true',
    'vision_proj': os.getenv("OFFLOAD_VISUAL_PROJ", 'false').lower() == 'true',
    'text_proj': os.getenv("OFFLOAD_TEXT_PROJ", 'false').lower() == 'true',
    'complete_encoders': os.getenv("OFFLOAD_COMPLETE_ENCODER", 'false').lower() == 'true',
    'cos_sim': os.getenv("OFFLOAD_COS_SIM", 'false').lower() == 'true'
}



