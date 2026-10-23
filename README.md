# Askvocate

Askvocate is a legal-tech platform with three runnable parts:

| Part | Tech | Default port | Purpose |
| --- | --- | --- | --- |
| `app/` | Android (Kotlin, Jetpack) | — | Client & lawyer mobile app |
| `backend/` | Spring Boot (Java 21) + local Python worker | `8080` | Auth, documents, verification, sessions |
| `ai-service/` | FastAPI (Python) | `8000` | Lawyer recommendation & legal-assistant chat |

Document verification (Aadhaar, PAN, Bar/COP/Degree/AIBE, selfie) runs **inside `backend/`** using the local Python worker in `backend/verification/`. The `ai-service` is **not** required to test documents; it is only for lawyer matching and the legal assistant.

---

## Prerequisites

- **JDK 21** (backend and Android builds)
- **Android Studio** (latest stable; project uses AGP `9.3.1`, `compileSdk 36`, `minSdk 28`)
- **Python 3.10+** (both `backend/verification` and `ai-service`)
- **Git**
- **Tesseract OCR** (English) for document verification
- A **MongoDB** database (Atlas or local) and a **Cloudinary** account
- Android device/emulator running **API 28+**

---

## Repository layout

```
Askvocate/
├── app/                     # Android app
├── backend/                 # Spring Boot API
│   └── verification/        # Local Python OCR/QR/face worker + models
├── ai-service/              # FastAPI matching/legal-assistant service
└── README.md
```

---

## 1. Clone & environment files

### Clone

```bash
git clone https://github.com/sudipkunduChrist/Askvocate.git
cd Askvocate
git checkout develop
```

### Copy the example env files

Windows (PowerShell):

```powershell
Copy-Item backend\.env.example backend\.env
Copy-Item app\.env.example app\.env
Copy-Item ai-service\.env.example ai-service\.env  # optional
```

macOS / Linux:

```bash
cp backend/.env.example backend/.env
cp app/.env.example app/.env
cp ai-service/.env.example ai-service/.env  # optional
```

### `backend/.env` (auto-loaded by the backend)

Copy `backend/.env.example` → `backend/.env` and fill in:

```env
# MongoDB
MONGO_URL=mongodb+srv://<user>:<password>@cluster.mongodb.net/askvocate

# JWT / sessions
JWT_SECRET=<a-long-random-secret>

# Cloudinary
CLOUDINARY_CLOUD_NAME=<cloud-name>
CLOUDINARY_API_KEY=<api-key>
CLOUDINARY_API_SECRET=<api-secret>

# Google Sign-In (must match app/.env)
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com

# Optional integrations
BIGDATACLOUD_DEVELOPMENT_ENABLED=true
BIGDATACLOUD_API_KEY=<key>

# Admin API password (default: askvocate-admin)
ADMIN_PASSWORD=<strong-password>

# Enables keyed duplicate hashes for Aadhaar/PAN/enrollment numbers
AADHAAR_HASH_KEY=<random-secret>

# Optional: force a specific Python for the verification worker
# VERIFICATION_PYTHON=C:\Path\To\python.exe
# VERIFICATION_WORKER_PATH=D:\Askvocate\backend\verification\worker.py
```

### `app/.env`

Copy `app/.env.example` → `app/.env`:

```env
GOOGLE_CLIENT_ID=<same-web-client-id>.apps.googleusercontent.com
```

### `ai-service/.env` (only for matching/legal-assistant features)

Copy `ai-service/.env.example` → `ai-service/.env`:

```env
NVIDIA_API_KEY=<key>
NVIDIA_MODEL=nvidia/nemotron-3-super-120b-a12b
# Optional fallbacks
GEMINI_API_KEY=<key>
GROQ_API_KEY=<key>
```

---

## 2. Install verification dependencies (needed before the backend can verify documents)

The worker needs Python packages **and** Tesseract.

### Windows (PowerShell)

```powershell
cd backend
py -3 -m pip install -r verification\requirements.txt
```

Install Tesseract (UB Mannheim build):

```powershell
winget install --id UB-Mannheim.TesseractOCR --exact
```

During setup, include the **English** language data. The default `C:\Program Files\Tesseract-OCR\tesseract.exe` is detected automatically. Otherwise set:

```powershell
$env:TESSERACT_CMD = "C:\Path\To\tesseract.exe"
```

### macOS

```bash
cd backend
python3 -m pip install -r verification/requirements.txt
brew install tesseract
```

Models are bundled in `backend/verification/models/` (YuNet, SFace, MiniFASNet). No runtime downloads.

---

## 3. Run the backend (port 8080)

### Windows

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
cd backend
./mvnw spring-boot:run
```

API base: `http://localhost:8080/api`

The backend binds to `0.0.0.0`, so a phone on the same Wi-Fi can reach it via the host's LAN IP.

---

## 4. Run the AI service (port 8000) — optional

Only needed for lawyer recommendation and legal-assistant chat.

### Windows (PowerShell)

```powershell
cd ai-service
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

If script execution is blocked:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

…or skip activation and run `.\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000`.

### macOS / Linux

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Required CSVs live in `ai-service/datasets/` (`cases.csv`, `advocate_details.csv`); embedding `.pkl` caches are generated if absent.

---

## 5. Run the Android app

Open the repository root in Android Studio, wait for Gradle sync, then edit:

`app/src/main/java/com/example/askvocate/network/ApiConfig.kt`

Choose the matching mode:

| Testing target | `BASE_URL` | `AI_BASE_URL` | Extra step |
| --- | --- | --- | --- |
| **Emulator** | `http://10.0.2.2:8080` | `http://10.0.2.2:8000` | none |
| **Physical phone (USB)** | `http://localhost:8080` | `http://localhost:8000` | `adb reverse` both ports |
| **Physical phone (Wi-Fi)** | `http://<LAN-IP>:8080` | `http://<LAN-IP>:8000` | allow ports in firewall |

`10.0.2.2` is the emulator's alias for the host machine and does **not** work on a physical phone.

### USB reverse ports

```bash
adb devices
adb reverse tcp:8080 tcp:8080
adb reverse tcp:8000 tcp:8000
```

Re-run `adb reverse` after unplugging, reconnecting, or restarting the device/emulator.

### Find the host LAN IP

- **Windows:** `ipconfig` → IPv4 Address (e.g. `192.168.1.42`)
- **macOS:** `ipconfig getifaddr en0` (Wi-Fi) or `ifconfig`

### Build & install

Windows PowerShell:

```powershell
.\gradlew.bat installDebug
```

macOS / Linux:

```bash
./gradlew installDebug
```

Or press **Run ▶** in Android Studio. The app needs **Camera** permission for document capture and the live selfie.

After pulling updates, re-check `ApiConfig.kt` — the URLs are local developer settings and may be changed by a teammate.

---

## 6. Recommended startup order

1. Configure `backend/.env` (MongoDB + Cloudinary).
2. Install verification Python deps + Tesseract.
3. Start **backend** (`:8080`).
4. Start **ai-service** (`:8000`) if using matching/legal-assistant.
5. Set `ApiConfig` for emulator/phone and run the app.

Document verification needs only steps 1–3 and 5.

---

## 7. Windows vs macOS quick reference

| Task | Windows (PowerShell) | macOS / Linux |
| --- | --- | --- |
| Backend run | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` |
| Backend tests | `.\mvnw.cmd test` | `./mvnw test` |
| Verification deps | `py -3 -m pip install -r verification\requirements.txt` | `python3 -m pip install -r verification/requirements.txt` |
| Verification tests | `py -3 -m unittest discover -s tests -v` | `python3 -m unittest discover -s tests -v` |
| AI service run | `python -m uvicorn main:app --host 0.0.0.0 --port 8000` | `python -m uvicorn main:app --host 0.0.0.0 --port 8000` |
| AI service tests | `py -3 -m unittest discover -s tests -v` | `python3 -m unittest discover -s tests -v` |
| Install APK | `.\gradlew.bat installDebug` | `./gradlew installDebug` |
| App unit tests | `.\gradlew.bat test` | `./gradlew test` |
| Host IP | `ipconfig` | `ipconfig getifaddr en0` |
| USB reverse | `adb reverse tcp:8080 tcp:8080` | `adb reverse tcp:8080 tcp:8080` |

Run the verification/AI test commands from `backend/verification/` or `ai-service/` respectively.

---

## 8. Troubleshooting

- **App can't reach the backend:** `10.0.2.2` is emulator-only. On a USB phone use `localhost` + `adb reverse`; on Wi-Fi use the LAN IP and check firewall.
- **`adb reverse` stops working:** re-run it after reconnect/restart.
- **`./mvnw` or `./gradlew` permission denied (macOS/Linux):** run `chmod +x mvnw gradlew` from the repo root, and `chmod +x mvnw` inside `backend/`.
- **Verification fails with "Python module missing":** run the verification `pip install` step with the same Python the backend finds. Set `VERIFICATION_PYTHON` explicitly.
- **Tesseract not found:** install it or set `TESSERACT_CMD` to the executable path.
- **Cloudinary/Mongo errors:** check the `.env` values and that the Cloudinary account/Atlas cluster allow access.
- **Admin API login fails:** use the `ADMIN_PASSWORD` from `backend/.env` (default `askvocate-admin`).
- **Port already in use:** stop the process using 8080 or 8000, or change the port.
- **Legal assistant/matching unavailable:** that's `ai-service`; document verification still works without it.

---

## 9. Contributions

1. Branch from `develop`: `git checkout -b feature-your-feature-name`.
2. Commit and push.
3. Open a Pull Request against `develop`.

| Branch | Purpose |
| --- | --- |
| `main` | Production-ready |
| `develop` | Integration |
| `feature-*` | Feature work |

© 2026 Askvocate Team
