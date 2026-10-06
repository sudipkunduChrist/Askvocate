# Backend document verifier

Spring invokes `worker.py` as a local Python subprocess for each OCR, signed Aadhaar QR,
or selfie check. The app calls only the Spring API on port 8080; document verification
does not call `ai-service` or require port 8000.

The Aadhaar upload runs OCR only and saves front/back OCR text in MongoDB. QR signature
and photo checks run later during live selfie verification.

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
The head-turn check is a basic replay deterrent and needs real-device calibration.
