# Askvocate Backend Setup and Local Testing Guide

This guide explains how to run the backend locally, configure environment values, and test the app on emulator, USB, and wireless device setups.

---

## 1. Prerequisites

Before starting, make sure you have:

- Java 21+
- Maven or the included Maven wrapper
- Android Studio for the app
- adb installed and available in PATH
- A phone or emulator
- A local MongoDB connection or remote MongoDB URL

---

## 2. Environment Configuration

Create a `.env` file inside the `backend` folder if it does not already exist.

Example:

```env
GOOGLE_WEB_CLIENT_ID=your_google_client_id_here.apps.googleusercontent.com
MONGO_URL=mongodb+srv://<username>:<password>@cluster.mongodb.net/askvocate
JWT_SECRET=your_jwt_secret_here
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

> Keep this file local and do not commit the real secrets.

---

## 3. Start the Backend

From the backend folder:

```bash
cd backend
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

The backend should run on:

```text
http://localhost:8080
```

---

## 4. Android App Setup

Create or update the Android app env file:

```env
GOOGLE_CLIENT_ID=your_google_client_id_here.apps.googleusercontent.com
```

This should be in:

```text
app/.env
```

Then open the project in Android Studio and let Gradle sync complete.

---

## 5. Testing on Emulator

For emulator-based testing, no special reverse proxy is usually needed because the emulator can reach the local backend directly.

Steps:

1. Start an Android emulator
2. Open the project in Android Studio
3. Select the app module
4. Click Run

---

## 6. Testing on Physical Device via USB

Steps:

1. Enable Developer Options on the phone
2. Enable USB debugging
3. Connect the phone to your PC using USB
4. Verify it is visible:

```powershell
adb devices
```

5. Forward the backend port:

```powershell
adb reverse tcp:8080 tcp:8080
```

This allows the Android app running on the device to access:

```text
http://localhost:8080
```

6. Run the app from Android Studio.

---

## 7. Testing on Phone Over Wi‑Fi (No USB)

Use this when USB is unavailable.

Steps:

1. Connect phone and PC to the same Wi‑Fi network
2. Enable USB debugging once for the initial setup
3. Set the device to TCP mode:

```powershell
adb tcpip 5555
```

4. Find the phone IP address in device settings
5. Connect to the phone:

```powershell
adb connect <PHONE_IP>:5555
```

6. Forward the backend port:

```powershell
adb reverse tcp:8080 tcp:8080
```

7. Run the app in Android Studio.

---

## 8. Google Sign-In Notes

Google Sign-In requires the correct OAuth configuration.

Check the following:

- The Web client ID in `app/.env` is valid
- The same value is present in `backend/.env`
- The Google Cloud Console OAuth client is configured correctly
- The Android package name matches the app
- The SHA-1 certificate matches the Android app signing configuration
- The backend is running before sign-in is tested

If the app shows:

```text
Google sign-in not configured
```

then verify:

- `app/.env` exists and contains `GOOGLE_CLIENT_ID`
- the value is not empty
- Android Studio has synced the Gradle project after the change
- the backend is running
- `adb reverse tcp:8080 tcp:8080` was executed for device testing

---

## 9. Common Commands

### Check connected devices

```powershell
adb devices
```

### Reverse port for local backend access

```powershell
adb reverse tcp:8080 tcp:8080
```

### Connect to device over Wi‑Fi

```powershell
adb tcpip 5555
adb connect <PHONE_IP>:5555
```

---

## 10. Quick Start Summary

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

Then for phone testing:

```powershell
adb reverse tcp:8080 tcp:8080
```

Finally run the app in Android Studio.

---

## 11. Recommended Workflow

For easiest development:

- Use emulator for fast validation
- Use USB device with `adb reverse` for stable local testing
- Use Wi‑Fi device setup for testing without cables

Always start the backend before testing authentication or API flows.

---

## 12. Final Notes

- Keep all real secrets in local `.env` files only
- Do not store actual Google Client IDs in source control
- Use emulator testing for quick iteration and real device testing before final validation
