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

#### Aadhaar

- Upload `front` **and** `back` to `POST /api/my-documents/AADHAAR`. Local Tesseract OCR reads the printed fields; OCR text is transient and never stored raw.
- The **signed UIDAI Secure QR** is the trust anchor. It must verify (signature + embedded photo) for any automatic outcome. Versioned V2/V3/V4 and legacy 16-field payloads are decoded. Masked cards/PDFs are accepted: the last four digits are matched against the QR.
- Outcomes:
  - `VERIFIED` — QR verifies, OCR succeeds, and printed fields match the QR (name, DOB, gender, pincode, address, last 4 digits). QR demographics are stored as `qrData`; the QR-embedded photo is **not** stored.
  - `PENDING` + `AADHAAR_OCR_MISMATCH` — QR verifies but printed fields differ. **Manual review.** Approval adopts the signed QR data, clears the mismatch, and keeps PAN/selfie usable.
  - `PENDING` + `AADHAAR_QR_UNREADABLE` / `AADHAAR_OCR_UNREADABLE` — quality failure. **No manual review**: the user must retry with a clearer photo or e-Aadhaar (same QR). Approval of a QR-less Aadhaar is rejected.
- Aadhaar images are retained because live selfie verification re-reads the QR from them.

#### PAN

- Upload the card photo/QR image to `POST /api/my-documents/PAN` (or scan the QR and send the text to `/api/my-documents/PAN/qr-text`). The signed PAN QR (ECDSA P-384/SHA-384) is unpacked into PII + photo and verified against the issuer key. **OCR alone never auto-verifies PAN.**
- A verified PAN must be an **Individual** PAN, structurally valid, not a duplicate of another account, and must match the verified Aadhaar QR (name, DOB).
- Outcomes:
  - `VERIFIED` — all checks pass; the submitted image is stored.
  - `PENDING` + `PAN_MISMATCH` / `PAN_DUPLICATE` — data conflict that a retry cannot fix. **Manual review.**
  - `PENDING` + `PAN_QR_UNREADABLE` — quality failure. **No manual review**: retry the scan/photo or upload e-PAN. The latest image is kept as evidence.
  - `PENDING` + `PAN_WAITING_AADHAAR` — PAN verified, waiting for Aadhaar. Auto-completes when Aadhaar verifies.
  - `FAILED` — organization/non-individual or structurally invalid PAN.
- Image retention: only `VERIFIED`, `PAN_QR_UNREADABLE`, and `PAN_WAITING_AADHAAR` keep the latest image. Mismatch/duplicate/org/invalid discard it. A new submission replaces (and deletes) the previous Cloudinary asset.

#### Role-based document sets

| Role | Required | Optional |
|---|---|---|
| `LAWYER_EXPERIENCED` | Bar enrollment **proof** (certificate preferred, or Bar Council ID), Certificate of Practice, AIBE certificate, Aadhaar, PAN, live selfie | — |
| `LAWYER_FRESHER` | Bar enrollment **proof** (certificate preferred, or Bar Council ID), LL.B. degree certificate, Aadhaar, PAN, live selfie | AIBE certificate |

"Bar enrollment proof" is one requirement satisfied by **either** the enrollment certificate **or** the Bar Council ID card; the certificate is preferred.

Bar Council, COP, Degree and AIBE uploads are OCR-extracted and **always go to manual review**
(`BAR_REVIEW`, `BAR_MISMATCH`, `BAR_DUPLICATE`, `DEGREE_REVIEW`, `AIBE_REVIEW`). Unreadable
uploads are `*_OCR_UNREADABLE` re-upload states. OCR never auto-verifies these certificates.
Completion percentage and "fully verified" are computed from the role's required set.

#### Manual review (admin)

- `POST /api/admin/login`, then `GET /api/admin/verification/documents/pending?type=PAN` or `?type=AADHAAR`.
- Only documents with `requiresManualReview=true` are queued: Aadhaar/PAN OCR↔QR mismatches, duplicate PAN, and Bar/COP/Degree/AIBE certificate reviews. QR/OCR-unreadable cases are re-upload states and are **not** queued.
- `POST /api/admin/verification/documents/{id}/approve|reject`.
- An Aadhaar without a verified Secure QR **cannot be approved**.
- Set `ADMIN_PASSWORD` in production; the default is `askvocate-admin`. Set `AADHAAR_HASH_KEY` to enable keyed duplicate detection. Bar Council verification retains its existing parsing and status rules.

#### Live selfie (after Aadhaar is verified)

Call `POST /api/documents/selfie/challenge?userId=...` to
obtain a single-use three-minute `challengeId` and random `expectedTurn`. The Android
lawyer profile has a **Verify live selfie** button that captures center, turned, and
returned frames directly from its front camera. For API testing, submit those frames
as multipart fields to `POST /api/documents/selfie/verify?userId=...` with `challengeId`.
The attempt is stored in `selfie_verifications` using the existing `DocType.SELFIE`.
The selfie step re-reads the signed Aadhaar QR from the stored Aadhaar images and compares
its embedded photo to the live capture; an Aadhaar without a readable signed QR cannot pass
selfie matching. Live capture is hardened with local quality gates (brightness, blur, face
size), a three-frame head-turn challenge, a local MiniFASNetV2 anti-spoof score, and a limit
of 5 selfie challenges per lawyer per hour. The QR photo and PAD thresholds are starting
points and must be calibrated on representative live/spoof samples. The overall lawyer
profile becomes `VERIFIED` only after Bar Council, Aadhaar, PAN, and a selfie tied to that
verified Aadhaar are all verified. A new Aadhaar submission requires a new selfie match.

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
`PENDING` items expose `requiresManualReview`, `pendingReason` and `required`
(`AADHAAR_QR_UNREADABLE`, `AADHAAR_OCR_UNREADABLE`, `AADHAAR_OCR_MISMATCH`,
`PAN_QR_UNREADABLE`, `PAN_MISMATCH`, `PAN_DUPLICATE`, `PAN_WAITING_AADHAAR`,
`BAR_*`, `DEGREE_*`, `AIBE_*`) so the app can show the right label and retry actions.

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
