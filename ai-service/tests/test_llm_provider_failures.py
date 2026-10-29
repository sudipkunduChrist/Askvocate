import unittest
from unittest.mock import patch

from src import llm_router


class ProviderFailureTests(unittest.TestCase):
    def test_nvidia_read_timeout_degrades_to_none(self):
        with (
            patch.object(llm_router, "_valid_key", return_value=True),
            patch.dict("os.environ", {"NVIDIA_API_KEY": "nvapi-test-key-0000000000"}),
            patch("urllib.request.urlopen", side_effect=TimeoutError("The read operation timed out")),
        ):
            self.assertIsNone(
                llm_router._classify_with_nvidia("Got into accident", ["Criminal Law"])
            )

    def test_classify_with_llm_survives_a_crashing_provider(self):
        with (
            patch.object(llm_router, "_classify_with_nvidia", side_effect=TimeoutError("timed out")),
            patch.object(llm_router, "_classify_with_gemini", return_value=None),
            patch.object(llm_router, "_classify_with_groq", return_value=None),
        ):
            self.assertIsNone(llm_router.classify_with_llm("Got into accident", ["Criminal Law"]))


if __name__ == "__main__":
    unittest.main()
