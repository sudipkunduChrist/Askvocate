package com.example.askvocate.network

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.askvocate.util.SessionManager
import com.example.askvocate.util.ToastType
import com.example.askvocate.util.showCustomToast
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Google Sign-In via AndroidX Credential Manager.
 *
 * Flow:
 *  1. [launchGoogleSignIn] shows the Google account picker (rendered by Play Services, no browser).
 *  2. On success we receive a signed ID token (JWT) containing email/name.
 *  3. [sendTokenToBackend] POSTs it to POST /api/users/auth/google where the server
 *     verifies it and logs the user in (auto-registering as CLIENT on first sign-in).
 *
 * IMPORTANT — one-time setup this depends on:
 *  - An Android OAuth client for package com.example.askvocate with this app's signing SHA-1.
 *  - A Web OAuth client whose ID is set as GOOGLE_WEB_CLIENT_ID in app/local.properties
 *    (gitignored, injected at build time) — and the SAME ID as GOOGLE_WEB_CLIENT_ID in backend/.env.
 */
object GoogleAuthHelper {

    /**
     * The "Web application" OAuth client ID from Google Cloud Console, injected from
     * app/local.properties at build time (see app/build.gradle.kts → buildConfigField).
     * (The Android client authorizes the app itself; the Web client is the token audience.)
     */
    private val WEB_CLIENT_ID: String = com.example.askvocate.BuildConfig.GOOGLE_WEB_CLIENT_ID

    /**
     * Launches the Google account picker and, on success, runs the whole sign-in:
     * token exchange with the backend + [onSuccess] on the main thread.
     * Invokes [onComplete] when finished regardless of success, error, or cancellation.
     */
    fun launchGoogleSignIn(
        context: Context,
        targetRole: String? = null,
        onComplete: (() -> Unit)? = null,
        onSuccess: () -> Unit
    ) {
        if (WEB_CLIENT_ID.isBlank()) {
            context.showCustomToast(
                "Google sign-in not configured — add GOOGLE_WEB_CLIENT_ID to app/local.properties",
                ToastType.ERROR
            )
            onComplete?.invoke()
            return
        }

        val activity = context.findActivity() ?: run {
            Log.e("GoogleAuth", "Context is not an Activity")
            context.showCustomToast("Google sign-in failed: Invalid Activity context", ToastType.ERROR)
            onComplete?.invoke()
            return
        }

        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // show all accounts, not just previously used
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                var attempts = 0
                while (attempts < 2) {
                    attempts++
                    try {
                        val result = credentialManager.getCredential(activity, request)
                        val credential = result.credential
                        val googleIdTokenCredential = try {
                            GoogleIdTokenCredential.createFrom(credential.data)
                        } catch (e: Exception) {
                            Log.e("GoogleAuth", "Failed to parse GoogleIdTokenCredential", e)
                            null
                        }
                        val idToken = googleIdTokenCredential?.idToken
                        if (idToken.isNullOrEmpty()) {
                            context.showCustomToast("Google sign-in returned no token", ToastType.ERROR)
                            return@launch
                        }
                        // Network work off the main thread
                        withContext(Dispatchers.IO) {
                            sendTokenToBackend(idToken, targetRole, context, onSuccess)
                        }
                        return@launch
                    } catch (e: GetCredentialException) {
                        Log.w("GoogleAuth", "Google sign-in cancelled or failed (attempt $attempts): ${e.type}")
                        when (e) {
                            is androidx.credentials.exceptions.GetCredentialCancellationException -> {
                                // User closed the picker — not an error worth a toast
                                return@launch
                            }
                            is androidx.credentials.exceptions.NoCredentialException -> {
                                if (attempts < 2) {
                                    continue // retry once instantly if Play Services was warming up
                                }
                                context.showCustomToast(
                                    "No Google account available. Make sure a Google account is added on this device.",
                                    ToastType.ERROR
                                )
                                return@launch
                            }
                            else -> {
                                context.showCustomToast("Google sign-in failed: ${e.localizedMessage}", ToastType.ERROR)
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GoogleAuth", "Google sign-in error", e)
                        context.showCustomToast("Google sign-in failed: ${e.message}", ToastType.ERROR)
                        return@launch
                    }
                }
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private fun Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** POSTs the ID token to the backend and handles the response. Runs on Dispatchers.IO. */
    private suspend fun sendTokenToBackend(
        idToken: String,
        targetRole: String?,
        context: Context,
        onSuccess: () -> Unit
    ) {
        try {
            val url = URL("${ApiConfig.BASE_URL}/api/users/auth/google")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000

            val body = JSONObject().apply {
                put("idToken", idToken)
                if (!targetRole.isNullOrBlank()) {
                    put("targetRole", targetRole)
                }
            }.toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            Log.d("GoogleAuth", "[$responseCode] /auth/google → $responseText")

            val json = runCatching { JSONObject(responseText) }.getOrNull()
            val isSuccess = responseCode in 200..299 && json?.optBoolean("success", false) == true

            withContext(Dispatchers.Main) {
                if (isSuccess) {
                    val userObj = json?.optJSONObject("user")
                    if (userObj != null) {
                        SessionManager.saveUser(
                            context = context,
                            userId = userObj.optString("id", ""),
                            name = userObj.optString("name", "User"),
                            emailOrPhone = userObj.optString("email", ""),
                            role = json.optString("role", ""),
                            authProvider = "GOOGLE",
                            profileImageUrl = json.optString("profileImageUrl", "")
                        )
                    }
                    val message = json?.optString("message", "Signed in with Google") ?: "Signed in with Google"
                    context.showCustomToast(message, ToastType.SUCCESS)
                    onSuccess()
                } else {
                    val errorMsg = json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Google sign-in rejected"
                    context.showCustomToast(errorMsg, ToastType.ERROR)
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Network error during Google auth", e)
            withContext(Dispatchers.Main) {
                context.showCustomToast(
                    "Network error — is the backend running (adb reverse tcp:8080 tcp:8080)?",
                    ToastType.ERROR
                )
            }
        }
    }

    /** Clears the cached credential so the next sign-in shows the account picker again (used on logout). */
    suspend fun clearCredentialState(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("GoogleAuth", "clearCredentialState failed: ${e.message}")
        }
    }
}
