"""Local, fail-closed Aadhaar QR and face verification.

The QR payload layout and RSA signature follow UIDAI's Secure QR specification.
Only signed demographics leave this module; the QR photo stays in memory.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
import os
import re
import unicodedata
import zlib

import cv2
import numpy as np
from cryptography import x509
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from PIL import Image
import pytesseract
from pytesseract import Output


ROOT = Path(__file__).resolve().parent
CERTIFICATES = (
    ROOT / "certs/uidai_offline_publickey_2026.cer",
    ROOT / "certs/uidai_secure_qr_2018.cer",
)
YUNET_MODEL = ROOT / "models/face_detection_yunet_2023mar.onnx"
SFACE_MODEL = ROOT / "models/face_recognition_sface_2021dec.onnx"
FACE_THRESHOLD = 0.363  # OpenCV Zoo SFace cosine baseline; calibrate on real QR photos.
MAX_IMAGE_BYTES = 10 * 1024 * 1024
MAX_QR_BYTES = 128 * 1024
TESSDATA = ROOT / "tessdata"


def local_ocr(image_bytes: bytes, side: str = "") -> dict:
    """Run free, English-only on-device OCR; never submit bytes to an OCR vendor."""
    if not os.environ.get("TESSERACT_CMD") and os.name == "nt":
        installed = Path(r"C:\Program Files\Tesseract-OCR\tesseract.exe")
        if installed.is_file():
            pytesseract.pytesseract.tesseract_cmd = str(installed)
    elif os.environ.get("TESSERACT_CMD"):
        pytesseract.pytesseract.tesseract_cmd = os.environ["TESSERACT_CMD"]
    image = _image(image_bytes)
    rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    os.environ["TESSDATA_PREFIX"] = str(TESSDATA)
    try:
        candidates = [(rgb, "--psm 6",
                       pytesseract.image_to_string(rgb, lang="eng", config="--psm 6", timeout=10))]
        if side in ("front", "back"):
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            enhanced = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
            if enhanced.shape[1] < 1400:
                scale = min(2.0, 1400 / enhanced.shape[1])
                enhanced = cv2.resize(enhanced, None, fx=scale, fy=scale,
                                      interpolation=cv2.INTER_CUBIC)
            for config in (("--psm 6", "--psm 4", "--psm 11") if side == "back"
                           else ("--psm 6", "--psm 11")):
                try:
                    candidates.append((enhanced, config,
                                       pytesseract.image_to_string(enhanced, lang="eng",
                                                                   config=config, timeout=10)))
                except (pytesseract.TesseractError, RuntimeError):
                    pass
        rank = _address_read_quality if side == "back" else _front_read_quality if side == "front" else _general_read_quality
        selected_image, selected_config, text = max(candidates, key=lambda entry: rank(entry[2]))
        data = pytesseract.image_to_data(selected_image, lang="eng", config=selected_config,
                                         output_type=Output.DICT, timeout=10)
    except (pytesseract.TesseractError, pytesseract.TesseractNotFoundError, RuntimeError) as exc:
        raise VerificationError("Local Tesseract OCR is unavailable.") from exc
    confidences = [float(value) for value in data["conf"] if float(value) >= 0]
    confidence = sum(confidences) / len(confidences) / 100 if confidences else 0.0
    return {"ocrText": text, "confidence": round(confidence, 4)}


def _general_read_quality(text: str) -> float:
    return len(re.findall(r"[A-Za-z]{2,}", text))


def _front_read_quality(text: str) -> float:
    score = _general_read_quality(text) * 0.1
    if re.search(r"\b[2-9]\d{3}[\s-]*\d{4}[\s-]*\d{4}\b", text):
        score += 20
    if re.search(r"\b(?:DOB|Date\s*of\s*Birth)\s*[:/-]?\s*\d{1,2}[/.-]\d{1,2}[/.-]\d{4}\b", text, re.I):
        score += 20
    if re.search(r"\b(?:Male|Female|Transgender)\b", text, re.I):
        score += 10
    if re.search(r"(?im)^\s*Name\s*[:/-]?\s*[A-Za-z]{2,}", text):
        score += 10
    return score


def _address_read_quality(text: str) -> float:
    """Favor complete address markers without rewarding extra OCR garbage words."""
    lines = text.splitlines()
    for index, line in enumerate(lines):
        label = re.match(r"^\s*Address\s*[:/-]?\s*(.*)$", line, re.IGNORECASE)
        if not label:
            continue
        content = label.group(1)
        for following in lines[index + 1:index + 7]:
            if re.match(r"^\s*(?:Address|VID|UIDAI|Aadhaar|www\.|1947)\b", following, re.IGNORECASE):
                break
            content += " " + following
            if re.search(r"\b[1-9]\d{5}\b", following):
                break
        pin = re.search(r"\b[1-9]\d{5}\b", content)
        if pin:
            content = content[:pin.end()]
        words = re.findall(r"[A-Za-z]+", content)
        if not words:
            return 0.0
        markers = sum(bool(re.search(pattern, content, re.I)) for pattern in (
            r"\b(?:S/O|D/O|C/O|W/O)\b", r"\b(?:PO|P\.O\.)\s*:",
            r"\b(?:DIST|DISTRICT)\s*:", r"\bSECTOR\b",
            r"\b(?:ROAD|STREET|NAGAR|VIHAR|VILLAGE|COLONY|BLOCK|LANE)\b",
        ))
        short_fragments = sum(len(word) <= 2 and word.upper() not in {"SO", "DO", "CO", "WO", "PO", "NO"}
                              for word in words)
        strange = len(re.findall(r"[~|§�]", content))
        return (10 + (25 if pin else 0) + 7 * markers
                + min(len(words), 16) * 0.4 - max(len(words) - 16, 0) * 1.2
                - 2 * short_fragments - 4 * strange)
    return 0.0


class VerificationError(ValueError):
    pass


@dataclass(frozen=True)
class SignedQr:
    data: dict[str, str]
    photo: bytes
    certificate_name: str


def _image(raw: bytes) -> np.ndarray:
    if not raw or len(raw) > MAX_IMAGE_BYTES:
        raise VerificationError("Image is empty or exceeds the 10 MB limit.")
    try:
        with Image.open(BytesIO(raw)) as preview:
            if preview.width * preview.height > 24_000_000:
                raise VerificationError("Image dimensions are too large.")
    except VerificationError:
        raise
    except (OSError, ValueError) as exc:
        raise VerificationError("Image could not be decoded.") from exc
    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise VerificationError("Image could not be decoded.")
    return image


def read_qr_text(image_bytes: bytes) -> str:
    image = _image(image_bytes)
    try:
        import zxingcpp
        for barcode in zxingcpp.read_barcodes(image):
            text = barcode.text.strip()
            if text.isascii() and text.isdecimal():
                return text
    except ImportError:
        pass
    detector = cv2.QRCodeDetector()
    for candidate in (image, cv2.resize(image, None, fx=2, fy=2)):
        text, _, _ = detector.detectAndDecode(candidate)
        if text.isascii() and text.isdecimal():
            return text
    raise VerificationError("No readable Aadhaar Secure QR code was found.")


def _decompress(decimal_text: str) -> bytes:
    if not decimal_text or len(decimal_text) > 10000 or not decimal_text.isascii() or not decimal_text.isdecimal():
        raise VerificationError("Invalid Secure QR payload.")
    compressed = int(decimal_text).to_bytes((int(decimal_text).bit_length() + 7) // 8, "big")
    try:
        stream = zlib.decompressobj(16 + zlib.MAX_WBITS)
        decoded = stream.decompress(compressed, MAX_QR_BYTES + 1)
        if len(decoded) > MAX_QR_BYTES or not stream.eof or stream.unused_data:
            raise VerificationError("Secure QR payload is incomplete or too large.")
        return decoded
    except zlib.error as exc:
        raise VerificationError("Secure QR payload is not valid gzip data.") from exc


def verify_qr_payload(decimal_text: str, certificates: tuple[Path, ...] = CERTIFICATES) -> SignedQr:
    payload = _decompress(decimal_text)
    if len(payload) < 300:
        raise VerificationError("Secure QR payload is incomplete.")
    signed, signature = payload[:-256], payload[-256:]
    matched_certificate = None
    for path in certificates:
        if not path.is_file():
            continue
        certificate = x509.load_pem_x509_certificate(path.read_bytes())
        if "UNIQUE IDENTIFICATION AUTHORITY OF INDIA" not in certificate.subject.rfc4514_string().upper():
            continue
        try:
            certificate.public_key().verify(signature, signed, padding.PKCS1v15(), hashes.SHA256())
            matched_certificate = path.name
            break
        except InvalidSignature:
            continue
    if matched_certificate is None:
        raise VerificationError("UIDAI Secure QR signature could not be verified with configured certificates.")

    # UIDAI format: 16 fields including the contact flag, separated by 0xFF.
    fields: list[str] = []
    cursor = 0
    for _ in range(16):
        end = signed.find(b"\xff", cursor)
        if end < 0:
            raise VerificationError("Secure QR demographic fields are incomplete.")
        fields.append(signed[cursor:end].decode("utf-8"))
        cursor = end + 1
    flag = fields[0]
    if flag not in ("0", "1", "2", "3"):
        raise VerificationError("Unsupported Secure QR contact-data flag.")
    reference, name, dob, gender = fields[1:5]
    if len(reference) < 4 or not reference[:4].isdigit() or not name.strip() or not dob.strip():
        raise VerificationError("Secure QR identity fields are incomplete.")
    contact_bytes = (32 if flag in "13" else 0) + (32 if flag in "23" else 0)
    photo_end = len(signed) - contact_bytes
    if photo_end <= cursor:
        raise VerificationError("Secure QR photograph is missing.")
    photo = signed[cursor:photo_end]
    try:
        with Image.open(BytesIO(photo)) as image:
            image.verify()
    except Exception as exc:
        raise VerificationError("Secure QR photograph cannot be decoded.") from exc
    address_parts = [fields[i].strip() for i in (5, 8, 13, 7, 9, 15, 14, 6, 12)]
    pincode = fields[10].strip()
    data = {
        "aadhaarLast4": reference[:4],
        "name": name.strip(),
        "dob": dob.strip(),
        "gender": gender.strip(),
        "address": ", ".join(part for part in address_parts if part),
        "pincode": pincode,
    }
    return SignedQr(data, photo, matched_certificate)


def decode_signed_qr(image_bytes: bytes) -> SignedQr:
    return verify_qr_payload(read_qr_text(image_bytes))


def _normal(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).casefold()
    return " ".join(re.findall(r"[\w]+", value, flags=re.UNICODE))


def _date(value: str) -> str:
    for fmt in ("%d/%m/%Y", "%d-%m-%Y", "%d.%m.%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(value.strip(), fmt).date().isoformat()
        except ValueError:
            pass
    return ""


def compare_printed_with_qr(printed: dict[str, str], qr: dict[str, str], masked_number: str) -> list[str]:
    mismatches = []
    if _normal(printed.get("name", "")) != _normal(qr["name"]):
        mismatches.append("name")
    if printed.get("dob"):
        if not _date(printed["dob"]) or _date(printed["dob"]) != _date(qr["dob"]):
            mismatches.append("dob")
    elif printed.get("yearOfBirth"):
        if printed["yearOfBirth"] != (_date(qr["dob"])[:4] if _date(qr["dob"]) else qr["dob"]):
            mismatches.append("yearOfBirth")
    else:
        mismatches.append("dob")
    gender = {"m": "male", "f": "female", "t": "transgender",
              "पुरुष": "male", "महिला": "female"}
    if gender.get(_normal(qr["gender"]), _normal(qr["gender"])) != gender.get(
            _normal(printed.get("gender", "")), _normal(printed.get("gender", ""))):
        mismatches.append("gender")
    if printed.get("pincode") != qr["pincode"]:
        mismatches.append("pincode")
    printed_tokens = set(_normal(printed.get("address", "")).split())
    qr_tokens = set(_normal(qr["address"]).split())
    useful = {word for word in qr_tokens if len(word) > 2 and not word.isdigit()}
    if not useful or len(printed_tokens & useful) / len(useful) < 0.7:
        mismatches.append("address")
    if not masked_number.endswith(qr["aadhaarLast4"]):
        mismatches.append("aadhaarNumber")
    return mismatches


def _qr_photo(qr: SignedQr) -> np.ndarray:
    with Image.open(BytesIO(qr.photo)) as image:
        rgb = np.asarray(image.convert("RGB"))
    return _face_size(cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR))


def _face_size(image: np.ndarray) -> np.ndarray:
    longest = max(image.shape[:2])
    target = max(640, min(1280, longest))
    if target == longest:
        return image
    return cv2.resize(image, None, fx=target / longest, fy=target / longest)


def _single_face(image: np.ndarray, detector: cv2.FaceDetectorYN) -> np.ndarray:
    detector.setInputSize((image.shape[1], image.shape[0]))
    _, faces = detector.detect(image)
    if faces is None or len(faces) != 1:
        raise VerificationError("Exactly one clearly visible face is required in each image.")
    return faces[0]


def _yaw(face: np.ndarray) -> float:
    # YuNet eye and nose landmarks; ratio is a simple head-turn challenge,
    # not a certified presentation-attack detector.
    eye_a = np.array(face[4:6])
    eye_b = np.array(face[6:8])
    nose = np.array(face[8:10])
    span = float(np.linalg.norm(eye_a - eye_b))
    if span < 1:
        raise VerificationError("Eye landmarks are too small for liveness check.")
    midpoint = (eye_a + eye_b) / 2
    return float((nose[0] - midpoint[0]) / span)


def match_live_selfie(qr: SignedQr, frames: list[bytes], expected_turn: str) -> dict:
    if len(frames) != 3 or expected_turn not in ("left", "right"):
        raise VerificationError("A three-frame center, turn, center capture is required.")
    if not YUNET_MODEL.is_file() or not SFACE_MODEL.is_file():
        raise VerificationError("Free local face models are not installed.")
    detector = cv2.FaceDetectorYN.create(str(YUNET_MODEL), "", (320, 320), 0.6, 0.3, 5000)
    recognizer = cv2.FaceRecognizerSF.create(str(SFACE_MODEL), "")
    images = [_face_size(_image(frame)) for frame in frames]
    faces = [_single_face(image, detector) for image in images]
    yaw = [_yaw(face) for face in faces]
    turn = yaw[1] - (yaw[0] + yaw[2]) / 2
    # Saved front-camera JPEGs are not mirrored; a user turn to their left moves the nose right in image coordinates.
    direction_ok = turn >= 0.12 if expected_turn == "left" else turn <= -0.12
    centered = abs(yaw[0] - yaw[2]) <= 0.13 and abs(yaw[0]) <= 0.18 and abs(yaw[2]) <= 0.18
    liveness_passed = bool(direction_ok and centered)
    qr_photo = _qr_photo(qr)
    reference_face = _single_face(qr_photo, detector)
    reference_feature = recognizer.feature(recognizer.alignCrop(qr_photo, reference_face))
    selfie_feature = recognizer.feature(recognizer.alignCrop(images[0], faces[0]))
    similarity = float(recognizer.match(reference_feature, selfie_feature, cv2.FaceRecognizerSF_FR_COSINE))
    return {
        "faceMatchScore": round(max(0.0, min(1.0, similarity)), 4),
        "faceMatchThreshold": FACE_THRESHOLD,
        "livenessPassed": liveness_passed,
        "livenessScore": round(min(1.0, abs(turn) / 0.25), 4),
        "matched": bool(liveness_passed and similarity >= FACE_THRESHOLD),
        "reason": None if liveness_passed and similarity >= FACE_THRESHOLD else
                  ("Head-turn challenge was not completed." if not liveness_passed
                   else "Selfie face did not match the signed Aadhaar QR photograph."),
    }
