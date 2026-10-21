import json
import unittest

from worker import serialize_result


class WorkerOutputTests(unittest.TestCase):
    def test_hindi_ocr_text_round_trips_through_legacy_windows_pipe(self):
        result = {"ocrText": "पता: नई दिल्ली", "confidence": 0.9}
        wire = serialize_result(result)
        wire.encode("cp1252")
        self.assertEqual(result, json.loads(wire))


if __name__ == "__main__":
    unittest.main()
