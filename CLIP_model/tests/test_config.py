import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class ConfigTest(unittest.TestCase):
    def test_vector_store_defaults_are_local_chroma_values(self):
        from utils import config

        self.assertTrue(config.VECTOR_STORE_ENABLED)
        self.assertEqual("./chroma_data", config.CHROMA_PERSIST_DIR)
        self.assertEqual("clip_image_embeddings", config.CHROMA_COLLECTION)
        self.assertEqual("clip-vit-l-14", config.EMBEDDING_MODEL_NAME)
        self.assertTrue(config.VECTOR_SEARCH_FALLBACK)


if __name__ == "__main__":
    unittest.main()
