# Askvocate Backend

Spring Boot REST API service for Askvocate — supporting User Authentication, Multi-Role Profiles (Client, Lawyer Fresher, Lawyer Experienced), and Document Verification.

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

### Lawyer Aadhaar OCR and live selfie verification

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

`POST /api/documents/verify` accepts Aadhaar `front` and `back`. Local Tesseract OCR
reads the English name, labeled date of birth, gender, and full printed number from the front;
it reads only the English `Address` field from the back. Multiple local English-only
Tesseract reads are compared for Aadhaar images. The selected OCR text is saved as
`ocrText.front` and `ocrText.back` in the MongoDB `documents` record, including when
field extraction fails. The Aadhaar document becomes `VERIFIED` when those fields
are extracted; this OCR status does not establish that the card is authentic. This
upload does not check the QR. PAN and Bar Council verification retain their existing
parsing and status rules.

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

- `POST /api/documents/upload?userId={userId}&docType={docType}`: Upload document to Cloudinary.
- `GET /api/documents/{userId}/{docType}/signed-url`: Generate signed 1-hour access URL.
- `GET /api/documents/user/{userId}`: Fetch all documents belonging to user.

---

## 🛡 Features & Quality Highlights

1. **Strict RFC-5322 Email Validation**: `@Email` pattern validation enforced on all auth DTOs (`BaseSignup`, `LoginRequest`).
2. **Unified Error Handling**: [`GlobalExceptionHandler`](file:///d:/Askvocate/backend/src/main/java/com/askvocate/backend/exception/GlobalExceptionHandler.java) handles validation errors and duplicate account exceptions gracefully.
3. **Structured Timestamps**: `createdAt` timestamps saved as standardized ISO-8601 UTC strings (`YYYY-MM-DDTHH:mm:ss.sssZ`).
4. **SLF4J Terminal Logging**: Real-time console logs printed on endpoint access and MongoDB persistence events.
