package com.example.askvocate.network

/**
 * ═══════════════════════════════════════════════════════════════════
 *  Central network configuration for the Askvocate Android app.
 * ═══════════════════════════════════════════════════════════════════
 *
 *  🎯 HOW THIS WORKS
 *  ─────────────────
 *  - BASE_URL   → Where the Spring Boot server lives (host + port)
 *  - Endpoints  → Paths to resources on that server (start with /api/...)
 *  - Retrofit   → Concatenates them: BASE_URL + Endpoint = Full URL
 *
 *  Example:
 *      BASE_URL  = "http://localhost:8080"
 *      Endpoint  = "/api/users/auth/google"
 *      Full URL  = "http://localhost:8080/api/users/auth/google"
 *
 *  🔧 CHANGE ONLY `BASE_URL` FOR SWITCHING TESTING MODES
 *  ────────────────────────────────────────────────────
 *  Mode 1 — USB (adb reverse, no Wi-Fi needed):
 *      adb reverse tcp:8080 tcp:8080
 *      BASE_URL = "http://localhost:8080"
 *
 *  Mode 2 — Same Wi-Fi (both on same router):
 *      Check `ipconfig` → e.g., 192.168.1.42
 *      BASE_URL = "http://192.168.1.42:8080"
 *
 *  Mode 3 — Production (later):
 *      BASE_URL = "https://api.askvocate.com"
 *
 *  ⚠️ DO NOT add `/api` to BASE_URL — it belongs in each Endpoint path.
 *  ⚠️ DO NOT add a trailing slash to BASE_URL.
 */
object ApiConfig {

    // ═══════════════════════════════════════════════════════════════
    //  🔧 CHANGE THIS ONE LINE DEPENDING ON TESTING MODE
    // ═══════════════════════════════════════════════════════════════
//    const val BASE_URL = "http://localhost:8080"

    // ADB forwards the emulator/device's localhost:8000 to this computer.
    // Run this after starting or reconnecting the emulator/device:
    // adb reverse tcp:8000 tcp:8000
    const val AI_BASE_URL = "http://localhost:8000"

    const val BASE_URL = "http://10.0.2.2:8080"
}
