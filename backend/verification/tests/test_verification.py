import gzip
from io import BytesIO
from pathlib import Path
import base64
import uuid
import unittest
import zlib
from unittest.mock import patch
from datetime import datetime, timedelta, timezone

import numpy as np
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from cryptography.x509.oid import NameOID
from PIL import Image

import verification
from verification import VerificationError, _address_read_quality, _front_read_quality, _pan_read_quality, compare_printed_with_qr, decode_pan_qr_from_scanned, local_ocr, verify_qr_payload, match_live_selfie


class PanQrTests(unittest.TestCase):
    def setUp(self):
        self.key = ec.generate_private_key(ec.SECP384R1())

    def key_struct(self, point):
        oid = b"1.3.132.0.34"
        return (b"\x03\x01" + b"\x00\x03" + b"ECC" + b"\x55\x00\x00\x00\x01"
                + len(oid).to_bytes(2, "big") + oid
                + (2 + len(point)).to_bytes(2, "big") + b"\x04\x61" + point)

    def pack(self, data):
        bits = []
        for byte in data:
            for index in range(8):
                bits.append((byte >> index) & 1)
        while len(bits) % 13:
            bits.append(0)
        chunks = []
        for index in range(0, len(bits), 13):
            value = sum(bits[index + offset] << offset for offset in range(13))
            chunks.append(f"{value:04d}")
        return "".join(chunks)

    def payload(self, values, tamper=False):
        elements = bytearray()
        for value in values:
            encoded = value.encode()
            elements += b"\x08\x02" + bytes([len(encoded)]) + encoded
        blob = b"\x03\x02" + zlib.compress(bytes(elements))
        metadata = 0xC5 if len(blob) < 256 else 0xCD
        length = len(blob).to_bytes(2, "big") if len(blob) >= 256 else bytes([len(blob)])
        block = bytes([metadata]) + length + blob
        message = (bytes([3, 0]) + (0x1E).to_bytes(4, "big") + bytes([0])
                   + (1).to_bytes(2, "big") + bytes([1]) + block + bytes([0]))
        der = self.key.sign(message, ec.ECDSA(hashes.SHA384()))
        r, s = decode_dss_signature(der)
        signature = r.to_bytes(48, "big") + s.to_bytes(48, "big")
        if tamper:
            signature = bytes([signature[0] ^ 1]) + signature[1:]
        outer = message + b"\x01" + b"\x00" + b"\x00\x00" + len(signature).to_bytes(2, "big") + signature
        point = self.key.public_key().public_bytes(Encoding.X962, PublicFormat.UncompressedPoint)
        patch.object(verification, "PAN_ECC_KEY_1",
                     base64.b64encode(self.key_struct(point)).decode()).start()
        self.addCleanup(patch.stopall)
        return self.pack(outer)

    def test_individual_pan_qr_decodes_and_verifies(self):
        result = decode_pan_qr_from_scanned(self.payload([
            "ABCPK1234L", "JOHN DOE", "RICHARD DOE", "01/01/1990"]))

        self.assertTrue(result.signature_verified)
        self.assertEqual("ABCPK1234L", result.data["pan"])
        self.assertEqual("JOHN DOE", result.data["name"])
        self.assertEqual("RICHARD DOE", result.data["fatherName"])
        self.assertEqual("01/01/1990", result.data["dob"])
        self.assertEqual("Individual", result.data["panType"])

    def test_company_pan_qr_has_organization_layout(self):
        result = decode_pan_qr_from_scanned(self.payload([
            "ABCPK1234L", "ACME WIDGETS PRIVATE LIMITED", "15/08/1947"]))

        self.assertTrue(result.signature_verified)
        self.assertEqual("Organization", result.data["panType"])
        self.assertEqual("15/08/1947", result.data["dateOfIncorporation"])

    def test_tampered_signature_is_not_trusted(self):
        result = decode_pan_qr_from_scanned(self.payload([
            "ABCPK1234L", "JOHN DOE", "RICHARD DOE", "01/01/1990"], tamper=True))

        self.assertFalse(result.signature_verified)


class AddressOcrSelectionTests(unittest.TestCase):
    def test_old_aadhaar_front_prefers_read_with_complete_english_name(self):
        header = "Government of India\n"
        footer = "\nDOB: 31/07/2000\nMale\n2345 6789 0123"
        weak = header + "Mane As" + footer
        complete = header + "Alex Morgan Rivera\nMane As si3Hl" + footer
        self.assertGreater(_front_read_quality(complete), _front_read_quality(weak))

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

    def test_pan_ocr_chooses_read_with_number_and_fields_without_heading(self):
        weak = "INCOME TAX DEPARTMENT\nName: Test Lawyer"
        complete = ("AAAPL1234C\nName: Test Lawyer\nFather's Name: Test Father\n"
                    "DOB: 01/02/1990")
        self.assertGreater(_pan_read_quality(complete), _pan_read_quality(weak))
        image = np.full((300, 500, 3), 255, dtype=np.uint8)
        with patch("verification._image", return_value=image), \
                patch("verification.pytesseract.image_to_string",
                      side_effect=[weak, complete, weak, weak, weak]) as ocr, \
                patch("verification.pytesseract.image_to_data", return_value={"conf": [40, 80]}):
            result = local_ocr(b"example", "pan")
        self.assertEqual(complete, result["ocrText"])
        self.assertIn(complete, result["candidates"])
        self.assertTrue(all(call.kwargs["lang"] == "eng" for call in ocr.call_args_list))

    def test_pan_quality_prefers_pass_with_labeled_dob(self):
        weak = "AAAPL1234C\nName: Test Lawyer\nFather's Name: Test Father"
        complete = weak + "\nD.O.B: 01 / 02 / 1990"
        self.assertGreater(_pan_read_quality(complete), _pan_read_quality(weak))


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

    def versioned_payload(self, version="V2", flag="0"):
        fields = [
            version, flag, "4626" + "17012024123456789", "Test Lawyer", "26-02-2006", "M",
            "S/O: Example", "Gautam Buddha Nagar", "", "L-113", "Noida", "201304",
            "Maharishi Nagar", "Uttar Pradesh", "Vivek Vihar", "", "Noida",
            "" if flag in "01" else "XXXXXX6789",
        ]
        signed = b"\xff".join(field.encode() for field in fields) + b"\xff" + self.photo
        if flag in "13":
            signed += b"mxcxxxl@gxxxxxxxx"
        signature = self.key.sign(signed, padding.PKCS1v15(), hashes.SHA256())
        return str(int.from_bytes(gzip.compress(signed + signature), "big"))

    def test_versioned_v2_v3_v4_payloads_are_supported(self):
        for version, flag in (("V2", "0"), ("V3", "2"), ("V4", "3")):
            with self.subTest(version=version, flag=flag):
                result = verify_qr_payload(self.versioned_payload(version, flag), (self.cert_path,))
                self.assertEqual("Test Lawyer", result.data["name"])
                self.assertEqual("201304", result.data["pincode"])
                self.assertEqual("4626", result.data["aadhaarLast4"])
                self.assertEqual(self.photo, result.photo)

    def test_verified_qr_extracts_signed_fields_and_photo(self):
        payload = self.payload()
        result = verify_qr_payload(payload, (self.cert_path,))
        self.assertEqual("Test Lawyer", result.data["name"])
        self.assertEqual("201304", result.data["pincode"])
        self.assertEqual("4626", result.data["aadhaarLast4"])
        self.assertEqual(self.photo, result.photo)
        self.assertEqual(payload, result.raw_text)

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
