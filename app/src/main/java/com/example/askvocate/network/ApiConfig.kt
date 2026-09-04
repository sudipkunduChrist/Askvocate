package com.example.askvocate.network

/**
 * Central network configuration for the Askvocate Android app.
 *
 * NOTE FOR PHYSICAL DEVICE TESTING (USB):
 * Run the following ADB command in your terminal while your physical device is connected via USB:
 *
 *     adb reverse tcp:8080 tcp:8080
 *
 * This tunnels all network requests to `http://localhost:8080` from your phone directly to your computer's Spring Boot server,
 * regardless of Wi-Fi changes or network switches!
 */
object ApiConfig {
    /** Change this single constant if you ever switch to a remote server or different IP */
    const val BASE_URL = "http://localhost:8080/api"
}
