import unittest

from src.llm_router import _gemini_models_for_key, _groq_models_for_key, _unique_models


class Model:
    def __init__(self, *, name=None, model_id=None, supported_actions=None):
        self.name = name
        self.id = model_id
        self.supported_actions = supported_actions


class GeminiModels:
    def list(self):
        return [
            Model(name="models/gemini-3.8-flash", supported_actions=["generateContent"]),
            Model(name="models/gemini-2.5-flash", supported_actions=["generateContent"]),
            Model(name="models/not-for-generation", supported_actions=["embedContent"]),
        ]


class GeminiClient:
    models = GeminiModels()


class GroqModels:
    def list(self):
        return type("Response", (), {"data": [
            Model(model_id="openai/gpt-oss-20b"),
            Model(model_id="qwen/qwen3.8-27b"),
        ]})()


class GroqClient:
    models = GroqModels()


class ModelSelectionTests(unittest.TestCase):
    def test_unique_models_normalizes_prefix_and_deduplicates(self):
        self.assertEqual(
            _unique_models(("models/example", "example", "", "other")),
            ("example", "other"),
        )

    def test_gemini_uses_only_models_available_to_the_key(self):
        self.assertEqual(
            _gemini_models_for_key(GeminiClient()),
            ("gemini-3.8-flash", "gemini-2.5-flash"),
        )

    def test_groq_uses_only_models_available_to_the_key(self):
        self.assertEqual(
            _groq_models_for_key(GroqClient()),
            ("openai/gpt-oss-20b", "qwen/qwen3.8-27b"),
        )


if __name__ == "__main__":
    unittest.main()
