# Askvocate Backend

Spring Boot REST API service for Askvocate — supporting User Authentication, Multi-Role Profiles (Client, Lawyer Fresher, Lawyer Experienced), and lawyer-only Document Verification.

---

## 🛠 Tech Stack

- **Framework**: Spring Boot `3.4+` (Java 21)
- **Database**: MongoDB Atlas (Spring Data MongoDB)
- **Security**: Spring Security + BCrypt Password Encoding
- **Validation**: Jakarta Bean Validation (`@Email`, `@NotBlank`, `@Size`)
- **Cloud Storage**: Cloudinary SDK (for Document Management)

---

## 🚀 Quick Start

### 1. Environment Configuration (`.env`)
Create a `.env` file in `backend/`:

```env
MONGO_URL=mongodb+srv://<username>:<password>@cluster.mongodb.net/askvocate
JWT_SECRET=your-256-bit-secret-key
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
```

### 2. Run Locally

```bash
# Build & Run
mvn clean spring-boot:run
```
Base API URL: `http://localhost:8080/api`

### Lawyer-only Aadhaar OCR and live selfie verification

Document verification is available only to `LAWYER_FRESHER` and `LAWYER_EXPERIENCED`
accounts. Client accounts are rejected on every verification and document endpoint.
Document verification runs under the Spring backend. Install the local worker's free
Python dependencies once with `python -m pip install -r verification/requirements.txt`
from the `backend` directory, and install Tesseract OCR. On Windows the default
`C:\Program Files\Tesseract-OCR\tesseract.exe` is detected; otherwise set `TESSERACT_CMD`.
Spring invokes `verification/worker.py` on demand, so **port 8000 and ai-service are
not needed for document or selfie verification**. On Windows it checks local Python
installations and the `py` launcher automatically; set `VERIFICATION_PYTHON` to a
specific executable if needed. If Spring starts from outside the repository
or `backend` directory, set `VERIFICATION_WORKER_PATH` to the absolute worker path.
Set a stable, secret `AADHAAR_HASH_KEY` to enable keyed
Aadhaar-number hashes for duplicate detection. The full Aadhaar number follows the
existing document schema; restrict access to the MongoDB collection and Cloudinary
images in deployment.
Before production rollout, reconcile that existing full-number/raw-image storage with
[UIDAI's offline-verification guidance](https://uidai.gov.in/images/Dos_and_Don_ts_for_Offline_Verification_Seeking_entities.pdf),
which calls for retaining only the last four digits and redacting stored card copies.

`POST /api/documents/verify` (lawyer accounts only) accepts Aadhaar `front` and `back`. Local Tesseract OCR
reads the English name, labeled date of birth, gender, and full printed number from the front;
it reads only the English `Address` field from the back. Multiple local English-only
Tesseract reads are compared for Aadhaar images. The selected OCR text is saved as
`ocrText.front` and `ocrText.back` in the MongoDB `documents` record, including when
field extraction fails. The Aadhaar document becomes `VERIFIED` only when a signed UIDAI
Secure QR is present and verifies, and the printed OCR fields match the QR data. A
printed-field mismatch is stored as `PENDING` for manual review; a missing or invalid QR
fails the upload and asks for a re-upload. OCR alone never establishes that a card is
authentic. Versioned UIDAI Secure QR payloads (V2/V3/V4) and legacy 16-field payloads are
both decoded. Masked Aadhaar cards/PDFs that show only the last four digits are accepted:
the last four are matched against the signed QR, while the full 12-digit number stays
unavailable because the QR does not contain it. PAN uses multiple English-only OCR reads too, and stores
the selected raw text in `ocrText.front`; a missing card heading alone does not reject
a PAN if its number and required labeled fields are readable. Bar Council verification
retains its existing parsing and status rules.

After Aadhaar is verified, call `POST /api/documents/selfie/challenge?userId=...` to
obtain a single-use three-minute `challengeId` and random `expectedTurn`. The Android
lawyer profile has a **Verify live selfie** button that captures center, turned, and
returned frames directly from its front camera. For API testing, submit those frames
as multipart fields to `POST /api/documents/selfie/verify?userId=...` with `challengeId`.
The attempt is stored in `selfie_verifications` using the existing `DocType.SELFIE`.
The selfie step checks the signed Aadhaar QR and compares its embedded photo to the
live capture; an Aadhaar upload without a readable signed QR cannot pass selfie matching.
The overall lawyer profile becomes `VERIFIED` only after Bar Council, Aadhaar, PAN,
and a selfie tied to that verified Aadhaar are all verified. A new Aadhaar submission
requires a new selfie match.

All OCR, QR, signature, and face checks run locally without a paid per-verification API.
Cloudinary image storage remains subject to its account quota and pricing.

---

## 🔑 Authentication & User Endpoints (`/api/users`)

All error responses return a standardized `200 OK` JSON envelope with `success: false` and a descriptive message (no server crashes or 500 exceptions).

### 1. User Login
- **Endpoint**: `POST /api/users/login`
- **Request Body**:
  ```json
  {
    "emailOrPhone": "lawyer@example.com",
    "password": "Password123"
  }
  ```
- **Description**: Authenticates against `clients`, `lawyers_fresher`, and `lawyers_experienced` MongoDB collections. Returns role & profile.

### 2. Client Registration
- **Endpoint**: `POST /api/users/register/client`
- **Request Body**:
  ```json
  {
    "name": "Jane Doe",
    "emailOrPhone": "jane@example.com",
    "password": "Password123",
    "confirmPassword": "Password123"
  }
  ```

### 3. Lawyer Registration (Fresher)
- **Endpoint**: `POST /api/users/register/lawyer/fresher`
- **Request Body**:
  ```json
  {
    "name": "Alex Smith",
    "emailOrPhone": "alex@example.com",
    "password": "Password123",
    "confirmPassword": "Password123"
  }
  ```

### 4. Lawyer Registration (Experienced)
- **Endpoint**: `POST /api/users/register/lawyer/experienced`
- **Request Body**:
  ```json
  {
    "name": "Sarah Connor",
    "emailOrPhone": "sarah@example.com",
    "password": "Password123",
    "confirmPassword": "Password123"
  }
  ```

---

## 📄 Document Endpoints (`/api/documents`)

The Android **My Documents** screen uses `Authorization: Bearer <appToken>` on
`/api/my-documents`. The catalog and every document operation are lawyer-only; client
accounts are rejected. `GET /api/my-documents` returns the lawyer verification catalog;
`GET /api/my-documents/{type}` returns one document's status and details;
`POST /api/my-documents/{type}` accepts multipart `front` and optional `back` images;
`GET /api/my-documents/{type}/media/{side}` serves account-scoped images for
preview and download. Aadhaar needs both images. Live selfie uses the existing
challenge and verify endpoints. Degree and AIBE certificates support upload and
view only; they are labeled unverified until a verification backend is added.

The older `/api/documents` upload, verification, listing, download URL, and deletion
routes also require an app token belonging to the requested user. Selfie challenge
and verification accept that token as well.

---

## 🛡 Features & Quality Highlights

1. **Strict RFC-5322 Email Validation**: `@Email` pattern validation enforced on all auth DTOs (`BaseSignup`, `LoginRequest`).
2. **Unified Error Handling**: [`GlobalExceptionHandler`](file:///d:/Askvocate/backend/src/main/java/com/askvocate/backend/exception/GlobalExceptionHandler.java) handles validation errors and duplicate account exceptions gracefully.
3. **Structured Timestamps**: `createdAt` timestamps saved as standardized ISO-8601 UTC strings (`YYYY-MM-DDTHH:mm:ss.sssZ`).
4. **SLF4J Terminal Logging**: Real-time console logs printed on endpoint access and MongoDB persistence events.

## Shared search history

The Android app writes AI matching requests to MongoDB `search_history` through
`/api/search-history`. A search is copied to `saved_searches` only when the user
selects **Save search** in the app. The web backend reads these same collections.
Both backends must point to the same MongoDB host **and database**; the web
backend's `MONGO_DATABASE` must match the database selected by the Spring
backend's `MONGO_URL`. Users with an app session created before this change
must sign in once more to receive an app API token.

## Legal guide chat history

The Android legal guide stores each conversation in MongoDB `saved_ai_chat`.
Authenticated client requests to `/api/ai-chats` create and list chats;
`/api/ai-chats/{id}` reads, updates, and deletes only that client's chat.
Each document contains the full message list, including lawyer matching results,
plus its owner, title, and creation/update timestamps. The app saves after each
message and shows past conversations under **Chat history** in the drawer.
