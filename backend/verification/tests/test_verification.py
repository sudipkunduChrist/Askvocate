import gzip
from io import BytesIO
from pathlib import Path
import uuid
import unittest
from unittest.mock import patch
from datetime import datetime, timedelta, timezone

import numpy as np
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.x509.oid import NameOID
from PIL import Image

from verification import VerificationError, _address_read_quality, compare_printed_with_qr, local_ocr, verify_qr_payload, match_live_selfie


class AddressOcrSelectionTests(unittest.TestCase):
    def test_prefers_complete_english_address_over_unlabeled_noise(self):
        mixed = "Ops: es 90 65 a 2:30 Hiej\nIndia"
        english = "Address: S/O Rahul Bajaj, L-113 Vivek Vihar\nNoida 201304\nVID: 1234"
        self.assertGreater(_address_read_quality(english), _address_read_quality(mixed))

    def test_clean_address_outranks_longer_read_with_inserted_words(self):
        clean = ("Address: S/O: Rahul Bajaj, L-113, VIVEK VIHAR, SECTOR - 82, Noida, "
                 "PO: Maharishi Nagar, DIST: Gautam Buddha Nagar, Uttar Pradesh - 201304")
        noisy = ("Address: Pea eith CEs S/O: Rahul Bajaj, L-113, VIVEK VIHAR, "
                 "SECTOR - Sipe eins a 82, Noida, PO: Maharishi Nagar, "
                 "DIST: Gautam Se RO Buddha Nagar, See ee oes Uttar Pradesh - 201304")
        self.assertGreater(_address_read_quality(clean), _address_read_quality(noisy))
        image = np.full((300, 500, 3), 255, dtype=np.uint8)
        with patch("verification._image", return_value=image), \
                patch("verification.pytesseract.image_to_string", side_effect=[noisy, noisy, clean, noisy]), \
                patch("verification.pytesseract.image_to_data", return_value={"conf": [80, 90]}):
            result = local_ocr(b"example", "back")
        self.assertEqual(clean, result["ocrText"])

    def test_back_ocr_uses_english_for_every_pass_and_selects_best_address(self):
        mixed = "Ops: es 90 65 a 2:30 Hiej"
        english = "Address: 12 Main Road\nNew Delhi 201304\nVID: 1234"
        image = np.full((300, 500, 3), 255, dtype=np.uint8)
        with patch("verification._image", return_value=image), \
                patch("verification.pytesseract.image_to_string", side_effect=[mixed, english, mixed, mixed]) as ocr, \
                patch("verification.pytesseract.image_to_data", return_value={"conf": [80, 90]}):
            result = local_ocr(b"example", "back")
        self.assertEqual(english, result["ocrText"])
        self.assertTrue(all(call.kwargs["lang"] == "eng" for call in ocr.call_args_list))


class SignedQrTests(unittest.TestCase):
    def setUp(self):
        self.key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,
                         "UNIQUE IDENTIFICATION AUTHORITY OF INDIA")])
        now = datetime.now(timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                .public_key(self.key.public_key()).serial_number(x509.random_serial_number())
                .not_valid_before(now - timedelta(days=1)).not_valid_after(now + timedelta(days=1))
                .sign(self.key, hashes.SHA256()))
        self.cert_path = Path(__file__).resolve().parent / f"_test_uidai_{uuid.uuid4().hex}.cer"
        self.cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        photo = BytesIO()
        Image.new("RGB", (120, 120), "white").save(photo, format="JPEG2000")
        self.photo = photo.getvalue()
        self.fields = ["0", "4626", "Test Lawyer", "26/02/2006", "M", "S/O: Example",
                       "Gautam Buddha Nagar", "", "L-113", "Noida", "201304", "Maharishi Nagar",
                       "Uttar Pradesh", "Vivek Vihar", "", "Noida"]

    def tearDown(self):
        self.cert_path.unlink(missing_ok=True)

    def payload(self, signed=None):
        if signed is None:
            signed = b"\xff".join(field.encode() for field in self.fields) + b"\xff" + self.photo
        signature = self.key.sign(signed, padding.PKCS1v15(), hashes.SHA256())
        return str(int.from_bytes(gzip.compress(signed + signature), "big"))

    def test_verified_qr_extracts_signed_fields_and_photo(self):
        result = verify_qr_payload(self.payload(), (self.cert_path,))
        self.assertEqual("Test Lawyer", result.data["name"])
        self.assertEqual("201304", result.data["pincode"])
        self.assertEqual("4626", result.data["aadhaarLast4"])
        self.assertEqual(self.photo, result.photo)

    def test_wrong_signing_key_is_rejected(self):
        other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        signed = b"\xff".join(field.encode() for field in self.fields) + b"\xff" + self.photo
        payload = str(int.from_bytes(gzip.compress(signed + other.sign(signed, padding.PKCS1v15(),
                                                                 hashes.SHA256())), "big"))
        with self.assertRaises(VerificationError):
            verify_qr_payload(payload, (self.cert_path,))

    def test_mismatch_prevents_automatic_approval(self):
        qr = verify_qr_payload(self.payload(), (self.cert_path,))
        printed = {"name": "Someone Else", "dob": "26/02/2006", "gender": "MALE",
                   "pincode": "201304", "address": qr.data["address"]}
        self.assertEqual(["name"], compare_printed_with_qr(printed, qr.data, "XXXX-XXXX-4626"))

    def test_matching_printed_fields_have_no_mismatch(self):
        qr = verify_qr_payload(self.payload(), (self.cert_path,))
        printed = {"name": "Test Lawyer", "dob": "26/02/2006", "gender": "MALE",
                   "pincode": "201304", "address": qr.data["address"]}
        self.assertEqual([], compare_printed_with_qr(printed, qr.data, "XXXX-XXXX-4626"))

    def test_blank_frames_cannot_pass_face_or_liveness(self):
        qr = verify_qr_payload(self.payload(), (self.cert_path,))
        frame = BytesIO()
        Image.new("RGB", (640, 640), "white").save(frame, format="JPEG")
        with self.assertRaises(VerificationError):
            match_live_selfie(qr, [frame.getvalue()] * 3, "left")


if __name__ == "__main__":
    unittest.main()
