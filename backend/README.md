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