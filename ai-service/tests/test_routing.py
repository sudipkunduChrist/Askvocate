from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pandas as pd


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.llm_router import _parse_json
from src.recommender import RecommendationEngine, _is_ambiguous_vehicle_seizure


class _FakeEmbedder:
    def encode(self, texts, **kwargs):
        return np.asarray([[1.0, 0.0] for _ in texts])


def _engine(local_intent: dict) -> RecommendationEngine:
    engine = RecommendationEngine.__new__(RecommendationEngine)
    engine.embedder = _FakeEmbedder()
    engine.domains = ["Banking & Finance", "Criminal Law", "Motor Accident Claims"]
    engine.lawyers = pd.DataFrame([
        {
            "record_id": "ADV-TEST",
            "advocate_name": "Test Advocate",
            "city": "Delhi",
            "practice_area_primary": "Banking and Finance",
            "practice_area_secondary": "Consumer Law",
            "experience_level": "8-15 years",
            "profile_rating": 4.5,
            "consultation_fee_inr": 1000,
        }
    ])
    engine.lawyer_embeddings = np.asarray([[1.0, 0.0]])
    engine._classify = lambda query, vector: local_intent
    return engine


def _local(confidence: float, margin: float, domain: str = "Motor Accident Claims") -> dict:
    return {
        "primary_domain": domain,
        "confidence": confidence,
        "margin": margin,
        "top3_domains": [{"domain": domain, "score": 0.4}],
        "ambiguous": margin < 0.05,
        "source": "local_vector_embedding",
    }


class LlmParsingTests(unittest.TestCase):
    def test_needs_context_does_not_require_a_domain(self):
        result = _parse_json(json.dumps({
            "status": "needs_context",
            "primary_domain": None,
            "confidence": 0.9,
            "reason": "The actor is missing.",
            "clarification_question": "Who seized it?",
        }), {"Banking & Finance"})
        self.assertEqual(result["status"], "needs_context")
        self.assertEqual(result["primary_domain"], "")

    def test_legacy_matched_response_remains_supported(self):
        result = _parse_json(
            '{"primary_domain":"Banking & Finance","confidence":0.8}',
            {"Banking & Finance"},
        )
        self.assertEqual(result["status"], "matched")


class RecommendationRoutingTests(unittest.TestCase):
    def test_vehicle_seizure_without_actor_is_context_sensitive(self):
        self.assertTrue(_is_ambiguous_vehicle_seizure("Bike seize krd bbina kuch bataye mereko"))
        self.assertFalse(_is_ambiguous_vehicle_seizure("Police seized my bike"))

    def test_borderline_local_result_is_sent_to_llm_and_can_request_context(self):
        engine = _engine(_local(0.647, 0.0567))
        llm_result = {
            "status": "needs_context",
            "primary_domain": "",
            "confidence": 0.92,
            "reason": "The person or authority that seized the bike is not stated.",
            "clarification_question": "Who seized the bike?",
            "source": "gemini:test",
        }
        with patch("src.recommender.classify_with_llm", return_value=llm_result):
            result = engine.recommend("bike seized without prior consent")
        self.assertTrue(result["needs_clarification"])
        self.assertEqual(result["route_used"], "needs_context")
        self.assertEqual(result["recommended_lawyers"], [])

    def test_high_confidence_local_result_skips_llm(self):
        engine = _engine(_local(0.91, 0.2, "Banking & Finance"))
        with patch("src.recommender.classify_with_llm") as llm:
            result = engine.recommend("bank loan EMI recovery dispute")
        llm.assert_not_called()
        self.assertEqual(result["route_used"], "local_confident")
        self.assertEqual(result["detected_domain"], "Banking & Finance")

    def test_uncertain_local_result_without_an_llm_does_not_return_lawyers(self):
        engine = _engine(_local(0.647, 0.0567))
        with patch("src.recommender.classify_with_llm", return_value=None):
            result = engine.recommend("bike seized without prior consent")
        self.assertEqual(result["route_used"], "local_unsure")
        self.assertTrue(result["needs_clarification"])
        self.assertEqual(result["recommended_lawyers"], [])

    def test_low_confidence_llm_match_requests_context(self):
        engine = _engine(_local(0.647, 0.0567))
        llm_result = {
            "status": "matched",
            "primary_domain": "Banking & Finance",
            "confidence": 0.4,
            "reason": "Weak match.",
            "source": "groq:test",
        }
        with patch("src.recommender.classify_with_llm", return_value=llm_result):
            result = engine.recommend("bike seized without prior consent")
        self.assertEqual(result["route_used"], "needs_context")
        self.assertEqual(result["recommended_lawyers"], [])


if __name__ == "__main__":
    unittest.main()
