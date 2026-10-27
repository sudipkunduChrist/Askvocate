# Backend document verifier

Spring invokes `worker.py` as a local Python subprocess for each OCR, signed Aadhaar QR,
or selfie check. The app calls only the Spring API on port 8080; document verification
does not call `ai-service` or require port 8000.

The verifier is lawyer-only: only `LAWYER_FRESHER` and `LAWYER_EXPERIENCED` accounts may
submit verification documents. Client accounts are rejected by the backend.

The Aadhaar upload runs OCR and verifies the signed UIDAI QR. The document is `VERIFIED`
only when a valid signed QR is found and the printed OCR fields match the QR data; a
mismatch becomes `PENDING` manual review, and a missing/invalid QR fails the upload.
Versioned V2/V3/V4 payloads and legacy 16-field payloads are supported. Masked Aadhaar
cards that show only the last four digits are accepted and matched against the QR's last
four digits. The worker also returns the QR-embedded photo as JPEG base64; Spring uploads
it once during Aadhaar verification and stores the pre-signed S3 URL in `qrData.photoUrl`.
Live selfie verification reuses the signed QR photo.

Install prerequisites once:

```powershell
cd D:\Askvocate\backend
python -m pip install -r verification\requirements.txt
```

Install Tesseract OCR with English support. Aadhaar OCR runs English-only. On Windows the worker detects
`C:\Program Files\Tesseract-OCR\tesseract.exe`; set `TESSERACT_CMD` for another location.
The backend locates a local Python 3 installation automatically on Windows. Set
`VERIFICATION_PYTHON` if it must use a particular executable. Set
`VERIFICATION_WORKER_PATH` if Spring runs outside the repository or `backend` directory.
The models and OCR language files live beside the worker; no download occurs during a
verification request. Keep the UIDAI public certificates current as signing keys change.

Run the local tests from this directory with `python -m unittest discover -s tests -v`.
Live selfie checks now combine a three-frame head-turn challenge, capture-quality gates
(brightness, blur, face size), and a local MiniFASNetV2 anti-spoof score
(`models/minifasnet_v2.onnx`, run with OpenCV DNN). Spring also limits each lawyer to
5 selfie challenges per hour. `FACE_THRESHOLD` and `PAD_THRESHOLD` are starting points,
not calibrated values; validate false-accept and false-reject rates on real live/spoof
samples before production use.

## BCI degree-institution registry

Law-degree recognition uses `src/main/resources/verification/bci-institutions.json`
(versioned colleges, derived universities and manual aliases). The bundled data is a
mirror of the BCI-approved directory published at `verify.exammint.in`, with the
official BCI portal recorded in the JSON metadata. It is a curated cache, not a live
scrape: verify entries against the official BCI list before production approval, and
refresh the JSON manually (or point `DEGREE_REGISTRY_PATH` at an operator-maintained
copy) when BCI publishes changes.

