# Askvocate Backend

Spring Boot backend application for Askvocate - Document Verification & Management Platform.

## 📋 Table of Contents
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Installation & Running](#installation--running)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [Logging](#logging)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)
- [Quick Commands](#quick-commands)

---

## 🛠 Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 4.1.0 | Application Framework |
| Java | 21+ | Programming Language |
| MongoDB Atlas | 5.8.0 | NoSQL Database |
| Cloudinary | 1.29.0 | File Storage & CDN |
| JWT | 0.11.5 | Authentication |
| Lombok | 1.18.46 | Code Generation |
| Maven | 3.9+ | Build Tool |

---

## 📋 Prerequisites

- **Java JDK 21** or higher
- **Maven 3.9** or higher
- **MongoDB Atlas Account** (or local MongoDB)
- **Cloudinary Account** (for file storage)
- **Postman** or **cURL** (for API testing)

---

## ⚙️ Environment Setup

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd askvocate-backend
```

### 2. Create `.env` File

Create a `.env` file in the project root directory:

```env
# MongoDB Atlas Connection
MONGO_URL=mongodb+srv://username:password@cluster.mongodb.net/database

# JWT Secret (minimum 256 bits)
JWT_SECRET=your-256-bit-secret-key-here-change-this

# Cloudinary Credentials (Get from https://cloudinary.com/console)
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
```

### 3. Get Your Credentials

#### MongoDB Atlas:
1. Go to [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Create a cluster
3. Get your connection string from "Connect" → "Connect your application"
4. Copy the URI and add to `.env`

#### Cloudinary:
1. Go to [Cloudinary](https://cloudinary.com/console)
2. Sign up (free tier available)
3. Copy your credentials from the dashboard:
    - Cloud Name
    - API Key
    - API Secret

#### JWT Secret:
Generate a secure secret:
```bash
# Linux/Mac
openssl rand -base64 32

# Windows PowerShell
[Convert]::ToBase64String((1..32 | % {Get-Random -Min 0 -Max 256}))
```

---

## 🚀 Installation & Running

### 1. Install Dependencies

```bash
mvn clean install
```

### 2. Build the Application

```bash
mvn clean package
```

### 3. Run the Application

#### Option A: Using Maven
```bash
mvn spring-boot:run
```

#### Option B: Using JAR
```bash
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

#### Option C: Using IntelliJ IDEA
1. Open the project in IntelliJ
2. Run `BackendApplication.main()`

### 4. Verify Application is Running

```bash
curl http://localhost:8080/actuator/health
```

Expected Response:
```json
{
  "status": "UP"
}
```

---

## 📚 API Documentation

### Base URL
```
http://localhost:8080
```

### Document Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/documents/upload?userId={userId}&docType={docType}` | Upload document |
| GET | `/api/documents/{userId}/{docType}/signed-url` | Get signed URL (1 hour) |
| GET | `/api/documents/user/{userId}` | Get all user documents |
| GET | `/api/documents/{userId}/{docType}` | Get specific document |
| DELETE | `/api/documents/{userId}/{docType}` | Delete document |

### Example API Calls

#### 1. Upload Document
```bash
curl -X POST "http://localhost:8080/api/documents/upload?userId=ShivangBajaj&docType=PAN" \
  -F "file=@/path/to/pan-card.jpg"
```

**Response:**
```json
{
  "success": true,
  "message": "Document uploaded successfully",
  "document": {
    "id": "67a8b3c4d5e6f7g8h9i0j1k2",
    "userId": "ShivangBajaj",
    "docType": "PAN",
    "fileUrl": "https://res.cloudinary.com/...",
    "uploadedAt": 1700000000000
  }
}
```

#### 2. Get Signed URL
```bash
curl "http://localhost:8080/api/documents/ShivangBajaj/PAN/signed-url"
```

**Response:**
```json
{
  "success": true,
  "signedUrl": "https://res.cloudinary.com/.../signed-url",
  "userId": "ShivangBajaj",
  "docType": "PAN",
  "expiresIn": "3600 seconds (1 hour)"
}
```

#### 3. Get All Documents
```bash
curl "http://localhost:8080/api/documents/user/ShivangBajaj"
```

**Response:**
```json
{
  "success": true,
  "documents": [
    {
      "id": "...",
      "userId": "ShivangBajaj",
      "docType": "PAN",
      "fileUrl": "https://cloudinary.com/...",
      "uploadedAt": 1700000000000
    }
  ],
  "count": 1
}
```

#### 4. Delete Document
```bash
curl -X DELETE "http://localhost:8080/api/documents/ShivangBajaj/PAN"
```

**Response:**
```json
{
  "success": true,
  "message": "Document deleted successfully"
}
```

---

## 📁 Project Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── askvocate/
│   │   │           └── backend/
│   │   │               ├── config/
│   │   │               │   ├── CloudinaryConfig.java
│   │   │               │   ├── MongoConfig.java
│   │   │               │   └── SecurityConfig.java
│   │   │               ├── controller/
│   │   │               │   ├── AuthController.java
│   │   │               │   └── DocumentController.java
│   │   │               ├── entity/
│   │   │               │   ├── DocType.java
│   │   │               │   ├── DocStatus.java
│   │   │               │   └── Verification_Status.java
│   │   │               ├── model/
│   │   │               │   ├── User.java
│   │   │               │   ├── UserDoc.java
│   │   │               │   └── VerificationQueueItem.java
│   │   │               ├── repository/
│   │   │               │   ├── UserDocRepository.java
│   │   │               │   └── VerificationQueueRepository.java
│   │   │               ├── service/
│   │   │               │   ├── CloudinaryService.java
│   │   │               │   ├── DocumentService.java
│   │   │               │   └── VerificationService.java
│   │   │               └── BackendApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application-dev.properties
│   └── test/
│       └── java/
│           └── com/
│               └── askvocate/
│                   └── backend/
│                       └── BackendApplicationTests.java
├── .env
├── .gitignore
├── pom.xml
└── README.md
```

---

## 🗄️ Database Schema

### MongoDB Collections

#### 1. `users` Collection
```json
{
  "_id": ObjectId("..."),
  "username": "ShivangBajaj",
  "email": "shivang@email.com",
  "password": "hashed_password",
  "role": "USER",
  "createdAt": 1700000000000
}
```

#### 2. `documents` Collection
```json
{
  "_id": ObjectId("..."),
  "userId": "ShivangBajaj",
  "docType": "PAN",
  "fileUrl": "https://cloudinary.com/...",
  "storagePath": "documents/ShivangBajaj/pan",
  "ocrProcessed": false,
  "tamperFlagged": false,
  "extractedData": null,
  "uploadedAt": 1700000000000
}
```

#### 3. `verification_queue` Collection
```json
{
  "_id": "ShivangBajaj",
  "userId": "ShivangBajaj",
  "submittedAt": 1700000000000,
  "status": "UNDER_VERIFICATION",
  "processedAt": null,
  "rejectionReason": null
}
```

---

## 📊 Logging

### Enable Debug Logging

Add to `application.properties`:
```properties
# MongoDB Logging
logging.level.org.mongodb.driver=DEBUG
logging.level.org.springframework.data.mongodb=DEBUG

# Cloudinary Logging
logging.level.com.cloudinary=DEBUG

# Application Logging
logging.level.com.askvocate.backend=DEBUG
```

### View Logs

```bash
# Console logs will show:
# 2026-08-09 17:08:05.450 INFO  - ✅ MongoDB connection test successful!
# 2026-08-09 17:08:06.677 INFO  - ✅ Cloudinary client created successfully!
```

---


### 4. Docker (Optional)

**Dockerfile:**
```dockerfile
FROM openjdk:21-jdk-slim
COPY target/backend-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t askvocate-backend .
docker run -p 8080:8080 --env-file .env askvocate-backend
```

---

## 🔧 Troubleshooting

### Issue 1: Application won't start - "Could not resolve placeholder 'CLOUDINARY_CLOUD_NAME'"
**Solution:** Add Cloudinary credentials to `.env` file

### Issue 2: MongoDB connection fails
**Solution:**
1. Check MongoDB Atlas IP whitelist (add your IP)
2. Verify credentials in `.env`
3. Check if network allows outbound connections

### Issue 3: Cloudinary upload fails
**Solution:**
1. Verify Cloudinary credentials
2. Check file size limits
3. Ensure file type is supported

### Issue 4: JWT authentication fails
**Solution:**
1. Regenerate JWT secret
2. Check token expiration
3. Verify token format

### Issue 5: File upload fails - "Empty file"
**Solution:** Ensure you're sending a valid file in multipart/form-data format

```bash
# Correct
curl -F "file=@/path/to/file.jpg" ...

# Incorrect
curl -F "file=" ...  # Empty file
```

---

## 📝 Configuration Reference

### Application Properties (`application.properties`)

```properties
spring.application.name=backend

# MongoDB
spring.data.mongodb.uri=${MONGO_URL}

# JWT
jwt.secret=${JWT_SECRET}
jwt.expiration=86400000

# Cloudinary
cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME}
cloudinary.api-key=${CLOUDINARY_API_KEY}
cloudinary.api-secret=${CLOUDINARY_API_SECRET}

# File Upload
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Server
server.port=8080

# Logging
logging.level.com.askvocate.backend=INFO
```

---

## 📄 .gitignore

```gitignore
# Compiled files
*.class
target/
*.jar
*.war

# IDE files
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# Environment files
.env
.env.local
.env.*.local

# Logs
logs/
*.log

# OS files
.DS_Store
Thumbs.db

# Application specific
application-local.properties
application-dev.properties
```

---

## 📌 Quick Commands

```bash
# Clean and Build
mvn clean package

# Run Application
mvn spring-boot:run

# Skip Tests
mvn clean package -DskipTests

# Run with Debug Mode
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# Check Dependencies
mvn dependency:tree

# Update Dependencies
mvn clean install -U

# Run Specific Profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---