"""One-shot, backend-owned OCR/QR/selfie worker. JSON enters and leaves on stdio."""

from __future__ import annotations

import base64
import binascii
import json
import sys

MAX_REQUEST_BYTES = 44 * 1024 * 1024


def image(value: str) -> bytes:
    try:
        return base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("Invalid image encoding.") from exc


def run(request: dict) -> dict:
    from verification import (VerificationError, compare_printed_with_qr,
                              decode_signed_qr, local_ocr, match_live_selfie)

    operation = request.get("operation")
    try:
        if operation == "ocr":
            return local_ocr(image(request["imageBase64"]), request.get("side", ""))
        if operation == "aadhaarQr":
            qr = decode_signed_qr(image(request["imageBase64"]))
            mismatches = compare_printed_with_qr(
                request["printedData"], qr.data, request["maskedDocumentNumber"])
            return {"verified": True, "data": qr.data, "photoAvailable": True,
                    "certificate": qr.certificate_name, "mismatchFields": mismatches,
                    "printedMismatch": bool(mismatches)}
        if operation == "selfie":
            qr = decode_signed_qr(image(request["qrImageBase64"]))
            frames = [image(frame) for frame in request["framesBase64"]]
            return match_live_selfie(qr, frames, request["expectedTurn"])
        raise ValueError("Unsupported verification operation.")
    except (VerificationError, ValueError, KeyError, TypeError) as exc:
        reason = str(exc) if isinstance(exc, VerificationError) else "Invalid verification request."
        if operation == "ocr":
            return {"ocrText": "", "confidence": 0.0, "reason": reason}
        if operation == "aadhaarQr":
            return {"verified": False, "reason": reason}
        return {"matched": False, "livenessPassed": False, "reason": reason}


def serialize_result(result: dict) -> str:
    # ASCII-safe JSON survives Windows pipes configured with a legacy code page.
    # Java's JSON parser restores the original Hindi/English Unicode text.
    return json.dumps(result, ensure_ascii=True)


def main() -> None:
    try:
        raw = sys.stdin.buffer.read(MAX_REQUEST_BYTES + 1)
        if len(raw) > MAX_REQUEST_BYTES:
            result = {"error": "Verification request exceeds the size limit."}
        else:
            request = json.loads(raw)
            result = run(request) if isinstance(request, dict) else {"error": "Invalid verification request."}
    except ModuleNotFoundError as exc:
        result = {"error": f"Python module {exc.name} is missing. Install backend/verification/requirements.txt."}
    except ImportError:
        result = {"error": "A Python verification dependency could not load. Check the backend Python installation."}
    except Exception:
        result = {"error": "Backend verification worker failed. Check its local dependencies."}
    sys.stdout.write(serialize_result(result))
    sys.stdout.flush()


if __name__ == "__main__":
    main()
