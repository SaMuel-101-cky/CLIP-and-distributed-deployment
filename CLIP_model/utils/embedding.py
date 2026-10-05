import numpy as np
import torch

from model import clip_loader as m
from utils.pred import get_model_device, preprocess_images


def normalize_features(features: torch.Tensor) -> torch.Tensor:
    return features / features.norm(dim=-1, keepdim=True)


def encode_images(model, image_paths):
    device = get_model_device(model)
    images = preprocess_images(image_paths, device)

    with torch.no_grad():
        features = model.encode_image(images)

    return normalize_features(features).detach().cpu().numpy().astype(np.float32)


def encode_texts(model, texts):
    device = get_model_device(model)
    prompts = ['a photo of a ' + text for text in texts]
    tokenized_prompts = m.tokenize(prompts).to(device)

    with torch.no_grad():
        features = model.encode_text(tokenized_prompts)

    return normalize_features(features).detach().cpu().numpy().astype(np.float32)
