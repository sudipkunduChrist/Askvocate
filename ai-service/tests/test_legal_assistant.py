from __future__ import annotations

import io
import json
import sys
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.legal_assistant import ASSISTANT_TIMEOUT_SECONDS, TIMEOUT_MESSAGE, chat


class LawyerMatchingHandoffTests(unittest.TestCase):
    def reply(self, response: dict | str, messages: list[dict] | None = None) -> dict:
        content = response if isinstance(response, str) else json.dumps(response)
        body = {"choices": [{"message": {"content": content}}]}
        with patch.dict("os.environ", {"NVIDIA_API_KEY": "test-key-for-local-tests"}), patch(
            "src.legal_assistant.urllib.request.urlopen",
            return_value=io.BytesIO(json.dumps(body).encode()),
        ):
            return chat(messages or [
                {"role": "user", "content": "My landlord in Pune withheld my rental deposit."},
                {"role": "assistant", "content": "When did your tenancy end?"},
                {"role": "user", "content": "Show lawyers for this case."},
            ])

    def test_explicit_request_offers_matching_while_gathering(self):
        query = "Rental deposit withheld by landlord in Pune; seeking recovery."
        result = self.reply({
            "status": "gathering",
            "reply": "Tap the button to find advocates for your rental dispute.",
            "lawyer_matching_requested": True,
            "recommendation_query": query,
        })
        self.assertEqual(result["status"], "gathering")
        self.assertEqual(result["recommendation_query"], query)

    def test_missing_query_uses_summary(self):
        result = self.reply({
            "reply": "Find advocates through Askvocate.",
            "lawyer_matching_requested": True,
            "case_summary": "Rental deposit dispute in Pune.",
        })
        self.assertEqual(result["recommendation_query"], "Rental deposit dispute in Pune.")

    def test_missing_summary_preserves_user_case_context_in_any_language(self):
        facts = "मेरे मकान मालिक ने पुणे में जमा राशि वापस नहीं की।"
        request = "इस मामले के लिए वकील दिखाओ।"
        result = self.reply({
            "reply": "वकील खोजने के लिए बटन दबाएँ।",
            "lawyer_matching_requested": True,
        }, [
            {"role": "user", "content": facts},
            {"role": "assistant", "content": "किरायेदारी कब समाप्त हुई?"},
            {"role": "user", "content": request},
        ])
        self.assertEqual(result["recommendation_query"], facts + "\n" + request)

    def test_regular_gathering_does_not_offer_matching(self):
        result = self.reply({
            "status": "gathering", "reply": "When did this happen?",
        }, [{"role": "user", "content": "My landlord withheld my deposit."}])
        self.assertEqual(result["recommendation_query"], "")

    def test_screenshot_plain_reply_shows_matching_and_removes_disclaimer(self):
        reply = "You can now tap the button to find lawyers through Askvocate’s own advocate matching screen."
        result = self.reply(reply + "\n\nI am not a lawyer. This is general information, not legal advice.")
        self.assertEqual(result["reply"], reply)
        self.assertTrue(result["lawyer_matching_requested"])
        self.assertIn("landlord in Pune", result["recommendation_query"])
        self.assertEqual(result["disclaimer"], "")

    def test_promised_button_with_missing_fields_still_offers_matching(self):
        for reply in (
            "Tap the button to find lawyers for this case.",
            "Tap Find matching lawyers to see advocates directly in this chat.",
        ):
            with self.subTest(reply=reply):
                result = self.reply({"reply": reply}, [
                    {"role": "user", "content": "My university refused to return my documents."}
                ])
                self.assertTrue(result["recommendation_query"])

    def test_user_request_without_provider_action_fields_offers_matching(self):
        result = self.reply({"status": "gathering", "reply": "I can help you find professional help."})
        self.assertIn("landlord in Pune", result["recommendation_query"])

    def test_query_without_ready_status_is_preserved(self):
        result = self.reply({"reply": "Find an advocate.", "recommendation_query": "Deposit dispute in Pune."})
        self.assertEqual(result["recommendation_query"], "Deposit dispute in Pune.")

    def test_disclaimer_field_is_empty_even_when_provider_supplies_one(self):
        result = self.reply({"reply": "Keep your rental agreement.", "disclaimer": "I am not a lawyer."})
        self.assertEqual(result["disclaimer"], "")

    def test_declined_matching_does_not_trigger_user_intent_fallback(self):
        result = self.reply({"reply": "What happened?"}, [
            {"role": "user", "content": "Don't recommend lawyers yet, just explain the process."}
        ])
        self.assertEqual(result["recommendation_query"], "")

    def test_ready_case_still_offers_matching_without_explicit_request(self):
        result = self.reply({
            "status": "ready", "reply": "You can find an advocate now.",
            "case_summary": "Rental deposit dispute in Pune.",
        })
        self.assertEqual(result["recommendation_query"], "Rental deposit dispute in Pune.")

    def test_out_of_scope_does_not_offer_matching(self):
        result = self.reply({
            "status": "out_of_scope", "reply": "Please describe a legal issue.",
            "lawyer_matching_requested": True, "recommendation_query": "Unrelated query",
        })
        self.assertEqual(result["recommendation_query"], "")

    def test_urgent_request_keeps_safety_guidance_and_matching(self):
        result = self.reply({
            "status": "urgent", "reply": "Contact emergency services now.",
            "lawyer_matching_requested": True, "recommendation_query": "Threats from landlord in Pune.",
        })
        self.assertEqual(result["status"], "urgent")
        self.assertEqual(result["reply"], "Contact emergency services now.")
        self.assertTrue(result["recommendation_query"])

    def test_matching_query_respects_endpoint_length_limit(self):
        result = self.reply({
            "reply": "Find advocates through Askvocate.",
            "lawyer_matching_requested": True, "case_summary": "case details " * 300,
        })
        self.assertEqual(len(result["recommendation_query"]), 2000)


class AssistantTimeoutTests(unittest.TestCase):
    def test_connection_and_wrapped_timeouts_have_retry_message(self):
        for error in (TimeoutError("timed out"), urllib.error.URLError(TimeoutError("timed out"))):
            with self.subTest(error=type(error).__name__), patch.dict(
                "os.environ", {"NVIDIA_API_KEY": "test-key-for-local-tests"}
            ), patch("src.legal_assistant.urllib.request.urlopen", side_effect=error) as provider:
                with self.assertRaises(TimeoutError) as raised:
                    chat([{"role": "user", "content": "My landlord withheld my deposit."}])
                self.assertEqual(str(raised.exception), TIMEOUT_MESSAGE)
                self.assertEqual(provider.call_args.kwargs["timeout"], ASSISTANT_TIMEOUT_SECONDS)
                self.assertEqual(provider.call_count, 1)

    def test_timeout_reading_response_body_has_retry_message(self):
        from unittest.mock import MagicMock

        response = MagicMock()
        response.__enter__.return_value.read.side_effect = TimeoutError("The read operation timed out")
        with patch.dict("os.environ", {"NVIDIA_API_KEY": "test-key-for-local-tests"}), patch(
            "src.legal_assistant.urllib.request.urlopen", return_value=response
        ):
            with self.assertRaises(TimeoutError) as raised:
                chat([{"role": "user", "content": "My landlord withheld my deposit."}])
        self.assertEqual(str(raised.exception), TIMEOUT_MESSAGE)

    def test_endpoint_returns_gateway_timeout_instead_of_internal_error(self):
        from fastapi import HTTPException
        from main import LegalAssistantRequest, legal_assistant

        request = LegalAssistantRequest(messages=[
            {"role": "user", "content": "My landlord withheld my deposit."}
        ])
        with patch("main.legal_assistant_chat", side_effect=TimeoutError(TIMEOUT_MESSAGE)):
            with self.assertRaises(HTTPException) as raised:
                legal_assistant(request)
        self.assertEqual(raised.exception.status_code, 504)
        self.assertEqual(raised.exception.detail, TIMEOUT_MESSAGE)

    def test_other_network_errors_remain_service_unavailability(self):
        with patch.dict("os.environ", {"NVIDIA_API_KEY": "test-key-for-local-tests"}), patch(
            "src.legal_assistant.urllib.request.urlopen",
            side_effect=urllib.error.URLError("connection refused"),
        ):
            with self.assertRaisesRegex(RuntimeError, "Could not reach"):
                chat([{"role": "user", "content": "My landlord withheld my deposit."}])


if __name__ == "__main__":
    unittest.main()
