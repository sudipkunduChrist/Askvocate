"""Local, fail-closed Aadhaar QR and face verification.

The QR payload layout and RSA signature follow UIDAI's Secure QR specification.
Only signed demographics leave this module; the QR photo stays in memory.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
import base64
import os
import re
import unicodedata
import zlib

import cv2
import numpy as np
from cryptography import x509
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec, padding
from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature
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


def _card_crop(image):
    """Find the largest card-shaped quadrilateral and perspective-warp it."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 50, 150)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    height, width = gray.shape
    best = None
    best_area = 0.0
    for contour in contours:
        perimeter = cv2.arcLength(contour, True)
        approx = cv2.approxPolyDP(contour, 0.02 * perimeter, True)
        if len(approx) != 4:
            continue
        area = cv2.contourArea(approx)
        if area < 0.15 * width * height:
            continue
        rect = cv2.minAreaRect(approx)
        rw, rh = rect[1]
        if rw == 0 or rh == 0:
            continue
        ratio = max(rw, rh) / min(rw, rh)
        if not 1.3 <= ratio <= 1.9:
            continue
        if area > best_area:
            best_area = area
            best = approx
    if best is None:
        return None
    points = best.reshape(4, 2).astype("float32")
    sums = points.sum(axis=1)
    diffs = np.diff(points, axis=1).reshape(-1)
    ordered = np.array([
        points[np.argmin(sums)],
        points[np.argmin(diffs)],
        points[np.argmax(sums)],
        points[np.argmax(diffs)],
    ], dtype="float32")
    target = np.array([[0, 0], [1400, 0], [1400, 882], [0, 882]], dtype="float32")
    matrix = cv2.getPerspectiveTransform(ordered, target)
    return cv2.warpPerspective(image, matrix, (1400, 882))


def _confident_text(data: dict, threshold: float = 30.0) -> str:
    """Rebuild text from word-level results, dropping low-confidence garbage."""
    required = ("text", "conf", "block_num", "par_num", "line_num")
    if not all(key in data for key in required):
        return ""
    lines: dict[tuple[int, int, int], list[str]] = {}
    for index, word in enumerate(data["text"]):
        word = (word or "").strip()
        if not word:
            continue
        try:
            confidence = float(data["conf"][index])
        except (TypeError, ValueError):
            continue
        if confidence < threshold:
            continue
        key = (data["block_num"][index], data["par_num"][index], data["line_num"][index])
        lines.setdefault(key, []).append(word)
    return "\n".join(" ".join(words) for _, words in sorted(lines.items()))


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
        if side in ("front", "back", "pan"):
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            enhanced = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
            if enhanced.shape[1] < 1400:
                scale = min(2.0, 1400 / enhanced.shape[1])
                enhanced = cv2.resize(enhanced, None, fx=scale, fy=scale,
                                      interpolation=cv2.INTER_CUBIC)
            for config in (("--psm 6", "--psm 4", "--psm 11") if side == "back"
                           else ("--psm 6", "--psm 4", "--psm 11") if side == "pan"
                           else ("--psm 6", "--psm 11")):
                try:
                    candidates.append((enhanced, config,
                                       pytesseract.image_to_string(enhanced, lang="eng",
                                                                   config=config, timeout=10)))
                except (pytesseract.TesseractError, RuntimeError):
                    pass
            if side == "pan":
                try:
                    threshold = cv2.adaptiveThreshold(
                        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 15)
                    candidates.append((threshold, "--psm 6",
                                       pytesseract.image_to_string(threshold, lang="eng",
                                                                   config="--psm 6", timeout=10)))
                except (pytesseract.TesseractError, RuntimeError):
                    pass
                cropped = _card_crop(image)
                if cropped is not None:
                    cropped_gray = cv2.cvtColor(cropped, cv2.COLOR_BGR2GRAY)
                    cropped_enhanced = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(cropped_gray)
                    try:
                        candidates.append((cropped_enhanced, "--psm 6",
                                           pytesseract.image_to_string(cropped_enhanced, lang="eng",
                                                                       config="--psm 6", timeout=10)))
                    except (pytesseract.TesseractError, RuntimeError):
                        pass
        rank = (_address_read_quality if side == "back" else _front_read_quality if side == "front"
                else _pan_read_quality if side == "pan" else _general_read_quality)
        if side == "pan" and max(rank(entry[2]) for entry in candidates) < 40:
            # Poor first read: try polarity, global threshold, rotation, and upscaling.
            extras = [cv2.bitwise_not(enhanced)]
            _, otsu = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            extras.append(otsu)
            for code in (cv2.ROTATE_90_CLOCKWISE, cv2.ROTATE_180, cv2.ROTATE_90_COUNTERCLOCKWISE):
                extras.append(cv2.rotate(enhanced, code))
            if enhanced.shape[1] < 2400:
                extras.append(cv2.resize(enhanced, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC))
            for extra in extras:
                try:
                    candidates.append((extra, "--psm 6",
                                       pytesseract.image_to_string(extra, lang="eng",
                                                                   config="--psm 6", timeout=10)))
                except (pytesseract.TesseractError, RuntimeError):
                    pass
        selected_image, selected_config, text = max(candidates, key=lambda entry: rank(entry[2]))
        data = pytesseract.image_to_data(selected_image, lang="eng", config=selected_config,
                                         output_type=Output.DICT, timeout=10)
    except (pytesseract.TesseractError, pytesseract.TesseractNotFoundError, RuntimeError) as exc:
        raise VerificationError("Local Tesseract OCR is unavailable.") from exc
    confidences = [float(value) for value in data["conf"] if float(value) >= 0]
    confidence = sum(confidences) / len(confidences) / 100 if confidences else 0.0
    candidate_texts = []
    for _, _, candidate in sorted(candidates, key=lambda entry: rank(entry[2]), reverse=True)[:6]:
        candidate = candidate.strip()
        if candidate and candidate not in candidate_texts:
            candidate_texts.append(candidate)
    filtered = _confident_text(data)
    if filtered and filtered not in candidate_texts:
        candidate_texts.append(filtered)
    return {"ocrText": text, "candidates": candidate_texts, "confidence": round(confidence, 4)}


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
    lines = text.splitlines()
    birth_line = next((index for index, line in enumerate(lines)
                       if re.search(r"\b(?:DOB|Date\s*of\s*Birth)\s*[:/-]?\s*\d{1,2}[/.-]\d{1,2}[/.-]\d{4}\b",
                                    line, re.I)), -1)
    if birth_line >= 0:
        name_scores = []
        for index in range(max(0, birth_line - 8), birth_line):
            line = lines[index].strip()
            if not re.fullmatch(r"[A-Za-z][A-Za-z .'-]{2,60}", line):
                continue
            if re.search(r"\b(?:name|address|dob|yob|gender|male|female|india|government|authority|"
                         r"uidai|aadhaar|enrolment|issue|print|year|birth|date|father|mother|"
                         r"photo|signature|valid|vid)\b", line, re.I):
                continue
            words = line.split()
            if len(words) >= 2:
                name_scores.append(min(len(line), 40) * 0.5 + min(len(words), 4) * 3
                                   - (birth_line - index) * 0.5)
        score += max(name_scores, default=0)
    return score


_PAN_DOB_LABEL = re.compile(
    r"\b(?:D[.\s]?[O0][.\s]?B\.?|Date\s*of\s*B[i1l]rth|Birth\s*Date)\b", re.I)
_PAN_DOB_VALUE = re.compile(
    r"\b(?:D[.\s]?[O0][.\s]?B\.?|Date\s*of\s*B[i1l]rth|Birth\s*Date)\b\s*[:/-]?\s*"
    r"\d{1,2}\s*[/.-]\s*\d{1,2}\s*[/.-]\s*\d{4}", re.I)


def _pan_read_quality(text: str) -> float:
    """Prefer the English OCR pass with a PAN number and all labeled identity rows."""
    score = _general_read_quality(text) * 0.1
    if re.search(r"\b[A-Z]{5}[\s-]*\d{4}[\s-]*[A-Z]\b", text, re.I):
        score += 30
    if re.search(r"(?im)^\s*Name\s*[:/-]?\s*[A-Za-z]{2,}", text):
        score += 15
    if re.search(r"(?im)^\s*Father'?s?\s+Name\s*[:/-]?\s*[A-Za-z]{2,}", text):
        score += 15
    if _PAN_DOB_VALUE.search(text):
        score += 25
    elif _PAN_DOB_LABEL.search(text):
        score += 5
    lines = text.splitlines()
    pan_line = next((i for i, line in enumerate(lines)
                     if re.search(r"\b[A-Z]{5}[\s-]*\d{4}[\s-]*[A-Z]\b", line, re.I)), -1)
    dob_line = next((i for i in range(pan_line + 1, len(lines))
                     if re.search(r"\b(?:DOB|Date\s*of\s*Birth)\b", lines[i], re.I)), len(lines))
    if pan_line >= 0:
        names = [line.strip() for line in lines[pan_line + 1:min(dob_line, pan_line + 12)]
                 if re.fullmatch(r"[A-Za-z][A-Za-z .'-]{2,70}", line.strip())
                 and not re.search(r"\b(?:name|father|income|tax|department|india|signature)\b", line, re.I)]
        score += min(len(names), 2) * 8
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
    raw_text: str = ""
    version: str = ""


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


def _split_qr_fields(signed: bytes, count: int) -> tuple[list[str], int]:
    fields: list[str] = []
    cursor = 0
    for _ in range(count):
        end = signed.find(b"\xff", cursor)
        if end < 0:
            raise VerificationError("Secure QR demographic fields are incomplete.")
        fields.append(signed[cursor:end].decode("utf-8"))
        cursor = end + 1
    return fields, cursor


def _qr_version(signed: bytes) -> str | None:
    marker = signed[:2]
    if marker in (b"V2", b"V3", b"V4"):
        return marker.decode("ascii")
    return None


def _jpeg2000_end(signed: bytes, start: int) -> int:
    end = signed.find(b"\xff\xd9", start)
    return end + 2 if end >= 0 else len(signed)


def _clean_flag(value: str) -> str:
    return value.strip().strip("\x00﻿ ").strip()


def _version_and_payload(signed: bytes) -> tuple[str | None, bytes]:
    probe = signed.lstrip(b"\x00 \t\r\n")
    marker = probe[:2]
    if marker[:1] == b"V" and marker[1:2].isdigit():
        return marker.decode("ascii"), probe
    return None, probe


def _first_field(payload: bytes) -> str:
    head = payload.split(b"\xff", 1)[0]
    return _clean_flag(head.decode("utf-8", "replace"))[:40]


def _parse_versioned_qr(payload: bytes):
    fields, photo_start = _split_qr_fields(payload, 18)
    flag = _clean_flag(fields[1])
    if flag not in ("0", "1", "2", "3"):
        raise VerificationError(f"unsupported contact flag {flag!r}")
    reference, name, dob, gender = fields[2:6]
    address_fields = fields[6:17]
    photo_end = _jpeg2000_end(payload, photo_start)
    photo = payload[photo_start:photo_end]
    address_parts = [address_fields[i].strip() for i in (0, 3, 8, 2, 4, 10, 9, 1, 7)]
    pincode = address_fields[5].strip()
    return reference, name, dob, gender, photo, address_parts, pincode


def _parse_legacy_qr(payload: bytes):
    fields, photo_start = _split_qr_fields(payload, 16)
    flag = _clean_flag(fields[0])
    if flag not in ("0", "1", "2", "3"):
        raise VerificationError(f"unsupported contact flag {flag!r}")
    reference, name, dob, gender = fields[1:5]
    contact_bytes = (32 if flag in "13" else 0) + (32 if flag in "23" else 0)
    photo_end = len(payload) - contact_bytes
    if photo_end <= photo_start:
        raise VerificationError("Secure QR photograph is missing.")
    photo = payload[photo_start:photo_end]
    address_parts = [fields[i].strip() for i in (5, 8, 13, 7, 9, 15, 14, 6, 12)]
    pincode = fields[10].strip()
    return reference, name, dob, gender, photo, address_parts, pincode


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

    version, parse_payload = _version_and_payload(signed)
    # Try the layout implied by the version first, then the other layout. The signature is
    # already verified, so retrying cannot invent data; it only tolerates payload variants.
    layouts = (True, False) if version is not None else (False, True)
    parsed = None
    failures: list[str] = []
    for versioned in layouts:
        try:
            parsed = (_parse_versioned_qr if versioned else _parse_legacy_qr)(parse_payload)
            break
        except VerificationError as exc:
            failures.append(f"{'versioned' if versioned else 'legacy'} layout: {exc}")
    if parsed is None:
        first = _first_field(parse_payload)
        raise VerificationError(
            f"Unsupported Secure QR payload (version={version or 'none'}, first field={first!r}; "
            + "; ".join(failures) + ")")
    reference, name, dob, gender, photo, address_parts, pincode = parsed

    if len(reference) < 4 or not reference[:4].isdigit() or not name.strip() or not dob.strip():
        raise VerificationError("Secure QR identity fields are incomplete.")
    if not photo:
        raise VerificationError("Secure QR photograph is missing.")
    try:
        with Image.open(BytesIO(photo)) as image:
            image.verify()
    except Exception as exc:
        raise VerificationError("Secure QR photograph cannot be decoded.") from exc
    data = {
        "aadhaarLast4": reference[:4],
        "name": name.strip(),
        "dob": dob.strip(),
        "gender": gender.strip(),
        "address": ", ".join(part for part in address_parts if part),
        "pincode": pincode,
    }
    return SignedQr(data, photo, matched_certificate, decimal_text, version or "")


def decode_signed_qr(image_bytes: bytes) -> SignedQr:
    return verify_qr_payload(read_qr_text(image_bytes))


# ── PAN Enhanced 2.0 Secure QR ───────────────────────────────────────
# Reference: incometax-pan-qr (MIT/Apache-2.0), which documents the wire
# format: a numeric QR string -> 13-bit chunks -> zlib PII/WebP image
# blobs -> P-384/SHA-384 ECDSA signature.

PAN_ECC_KEY_1 = "AwEAA0VDQ1UAAAABAAwxLjMuMTMyLjAuMzQAYwRhBI1vbBVnA1KE/T1UpdQYzG6LLot++cuCP5DdEdeKtedw5G8RKAhU0KbNXVUwym8CSwUyzdAPC98DAgvkJGOZA/x+cnJOWhVvYTqJvy+IlcOgjSe9kqs0O7zEBy26UmvlIw=="
PAN_ECC_KEY_2 = "AwEAA0VDQ1UAAAABAAwxLjMuMTMyLjAuMzQAYwRhBJ+fsFQNaIohp5JnCmGArWA5i25WAKHqFYnOEpRYsVmxK/O2W7iIy2T9x3vkZHaZm661w93VNc/99coCSzL92c1x9y5zxQPJCUztH2kT/EwGphLgvKKe2tK/rKMjNDMpSA=="
PAN_VERSION_KEY_1 = {0x1E, 0x20}
PAN_VERSION_KEY_2 = {0x1F, 0x21}
PAN_WHITELISTED_VERSIONS = {0x9990, 0x998F, 0x1E, 0x20, 0x998E, 0x998D, 0x1F, 0x21}
PAN_SCBLOB_PII = 0x0302
PAN_SCBLOB_IMAGE = 0x0102
PAN_STRUCTURE = re.compile(r"^[A-Z]{3}[PCHFATBLJG][A-Z][0-9]{4}[A-Z]$")


@dataclass(frozen=True)
class PanQr:
    data: dict[str, str]
    photo: bytes | None
    version: int
    signature_verified: bool
    raw_text: str


class _PanCursor:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, count: int) -> bytes:
        if self.pos + count > len(self.data):
            raise VerificationError("PAN Secure QR payload is incomplete.")
        value = self.data[self.pos:self.pos + count]
        self.pos += count
        return value

    def u8(self) -> int:
        return self.take(1)[0]

    def u16(self) -> int:
        return int.from_bytes(self.take(2), "big")

    def u32(self) -> int:
        return int.from_bytes(self.take(4), "big")


def read_pan_qr_text(image_bytes: bytes) -> str:
    image = _image(image_bytes)

    def usable(text: str) -> bool:
        text = text.strip()
        return bool(text) and text.isascii() and text.isdecimal() and len(text) % 4 == 0

    variants = [image]
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    variants.append(gray)
    _, otsu = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    variants.append(otsu)
    variants.append(cv2.bitwise_not(gray))
    if image.shape[1] < 2200:
        variants.append(cv2.resize(image, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC))
    if image.shape[1] > 600:
        variants.append(cv2.resize(image, None, fx=0.5, fy=0.5, interpolation=cv2.INTER_AREA))

    other_text = None
    try:
        import zxingcpp
        for variant in variants:
            try:
                for barcode in zxingcpp.read_barcodes(variant):
                    text = (barcode.text or "").strip()
                    if usable(text):
                        return text
                    if text:
                        other_text = other_text or text
            except Exception:
                continue
        for variant in variants[:2]:
            try:
                for barcode in zxingcpp.read_barcodes(variant, is_pure=True):
                    text = (barcode.text or "").strip()
                    if usable(text):
                        return text
                    if text:
                        other_text = other_text or text
            except Exception:
                continue
    except ImportError:
        pass

    detector = cv2.QRCodeDetector()
    for variant in variants:
        try:
            text, _, _ = detector.detectAndDecode(variant)
        except Exception:
            continue
        text = (text or "").strip()
        if usable(text):
            return text
        if text:
            other_text = other_text or text
    if other_text:
        raise VerificationError("Scanned QR is not the signed Enhanced PAN QR.")
    raise VerificationError("No readable PAN Secure QR code was found.")


def _pan_unpack(scanned: str) -> bytes:
    if not scanned or len(scanned) % 4 or not scanned.isascii() or not scanned.isdecimal():
        raise VerificationError("Invalid PAN Secure QR payload.")
    output = bytearray()
    carry = 0
    carry_bits = 0
    for index in range(0, len(scanned), 4):
        chunk = int(scanned[index:index + 4])
        if chunk > 8191:
            raise VerificationError("Invalid PAN Secure QR chunk.")
        value = chunk & 0x1FFF
        remaining = 13
        if carry_bits > 0 and carry_bits + remaining >= 8:
            combined = (carry | (value << carry_bits)) & 0xFF
            carry = combined
            value >>= 8 - carry_bits
            remaining -= 8 - carry_bits
            output.append(combined)
            carry = 0
            carry_bits = 0
        while remaining // 8 > 0:
            output.append(value & 0xFF)
            value >>= 8
            remaining -= 8
        if remaining > 0:
            carry = ((value << carry_bits) | carry) & 0xFF
            carry_bits += remaining
    return bytes(output)


def _pan_block(cursor: _PanCursor) -> tuple[int, bytes]:
    metadata = cursor.u8()
    control_type = (((metadata >> 0) & 1) << 2) | (((metadata >> 1) & 1) << 1) | ((metadata >> 2) & 1)
    exceed_length = bool((metadata >> 3) & 1)
    length = cursor.u16() if exceed_length else cursor.u8()
    return control_type, cursor.take(length)


def _pan_outer(unpacked: bytes) -> tuple[int, list[tuple[int, bytes]], bytes, bytes]:
    cursor = _PanCursor(unpacked)
    cursor.u8()  # code_type
    cursor.u8()  # reserved_0
    version = cursor.u32()  # reserved_1
    cursor.u8()  # reserved_2
    reserved_3 = cursor.u16()
    if version not in PAN_WHITELISTED_VERSIONS or reserved_3 > 6:
        raise VerificationError("Unsupported PAN Secure QR version.")
    blocks = []
    for _ in range(cursor.u8()):
        blocks.append(_pan_block(cursor))
    for _ in range(cursor.u8()):
        blocks.append(_pan_block(cursor))
    message = unpacked[:cursor.pos]
    if cursor.u8() != 1:
        raise VerificationError("Invalid PAN Secure QR signature header.")
    cursor.u8()  # signature_scheme
    cursor.take(2)  # padding
    signature = cursor.take(cursor.u16())
    if any(cursor.data[cursor.pos:]):
        raise VerificationError("Invalid PAN Secure QR trailing data.")
    return version, blocks, message, signature


def _pan_inflate(payload: bytes) -> bytes:
    try:
        stream = zlib.decompressobj()
        inflated = stream.decompress(payload, MAX_QR_BYTES + 1)
        if len(inflated) > MAX_QR_BYTES or not stream.eof or stream.unused_data:
            raise VerificationError("PAN Secure QR PII is incomplete or too large.")
        return inflated
    except zlib.error as exc:
        raise VerificationError("PAN Secure QR PII is not valid zlib data.") from exc


def _pan_pii(data: bytes) -> dict[str, str]:
    payloads: list[str] = []
    index = 0
    while index + 2 < len(data):
        if data[index] == 0x08 and data[index + 1] == 0x02:
            length = data[index + 2]
            start = index + 3
            end = start + length
            if end > len(data):
                raise VerificationError("PAN Secure QR PII is incomplete.")
            payloads.append(data[start:end].decode("utf-8", "replace"))
            index = end
        else:
            index += 1
    if len(payloads) >= 4:
        pan, name, father, dob = (value.strip() for value in payloads[:4])
        return {
            "pan": pan, "name": name, "fatherName": father, "dob": dob,
            "panType": "Individual", "panValid": "true" if PAN_STRUCTURE.fullmatch(pan.upper()) else "false",
        }
    if len(payloads) == 3:
        pan, name, incorporation = (value.strip() for value in payloads)
        return {
            "pan": pan, "name": name, "dateOfIncorporation": incorporation,
            "panType": "Organization", "panValid": "true" if PAN_STRUCTURE.fullmatch(pan.upper()) else "false",
        }
    raise VerificationError("PAN Secure QR PII is incomplete.")


def _pan_photo(data: bytes) -> bytes | None:
    if len(data) < 16:
        return None
    fixed = b"RIFF" + data[2:6] + b"WEBP" + b"VP8 " + data[16:]
    try:
        with Image.open(BytesIO(fixed)) as image:
            image.load()
            converted = BytesIO()
            image.convert("RGB").save(converted, format="JPEG", quality=90)
            return converted.getvalue()
    except Exception:
        return None


def _pan_verify(version: int, message: bytes, signature: bytes) -> bool:
    key_b64 = PAN_ECC_KEY_1 if version in PAN_VERSION_KEY_1 else (
        PAN_ECC_KEY_2 if version in PAN_VERSION_KEY_2 else None)
    if not key_b64 or len(signature) != 96:
        return False
    try:
        key_data = base64.b64decode(key_b64)
        cursor = _PanCursor(key_data)
        if cursor.take(2) != b"\x03\x01":
            return False
        cursor.take(2)
        if cursor.take(3) != b"ECC":
            return False
        cursor.take(5)
        cursor.take(cursor.u16())  # curve OID
        key = cursor.take(cursor.u16())
        public_key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP384R1(), key[2:])
        r = int.from_bytes(signature[:48], "big")
        s = int.from_bytes(signature[48:], "big")
        public_key.verify(encode_dss_signature(r, s), message, ec.ECDSA(hashes.SHA384()))
        return True
    except (InvalidSignature, ValueError, TypeError):
        return False


def decode_pan_qr_from_scanned(scanned: str) -> PanQr:
    unpacked = _pan_unpack(scanned)
    version, blocks, message, signature = _pan_outer(unpacked)
    pii = None
    photo = None
    for control_type, block in blocks:
        if control_type != 5:
            continue
        cursor = _PanCursor(block)
        identifier = cursor.u16()
        payload = block[cursor.pos:]
        if identifier == PAN_SCBLOB_PII:
            pii = _pan_pii(_pan_inflate(payload))
        elif identifier == PAN_SCBLOB_IMAGE:
            photo = _pan_photo(payload)
    if pii is None:
        raise VerificationError("PAN Secure QR does not contain PII.")
    return PanQr(pii, photo, version, _pan_verify(version, message, signature), scanned)


def decode_pan_qr(image_bytes: bytes) -> PanQr:
    return decode_pan_qr_from_scanned(read_pan_qr_text(image_bytes))


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
