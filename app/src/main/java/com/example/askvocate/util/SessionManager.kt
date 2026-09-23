package com.example.askvocate.util

import android.content.Context
import com.example.askvocate.network.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Persistent session state backed by SharedPreferences.
 *
 * On a fresh install (or reinstall) there is no saved session, so the app
 * starts from the sign-up / sign-in flow. After a successful sign-in or
 * sign-up the session is stored and the app keeps the user signed in no
 * matter how often the app is relaunched, removed from memory, or how the
 * user navigates back. The only ways to leave the signed-in state are
 * pressing Logout on the profile page or reinstalling the app.
 */
object SessionManager {

    enum class ValidationResult { VALID, INVALID, UNAVAILABLE }

    private const val PREFS_NAME = "askvocate_session"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_AUTH_PROVIDER = "auth_provider"
    private const val KEY_PROFILE_IMAGE_URL = "profile_image_url"

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
        if (!loggedIn) {
            clearUser(context)
            context.applicationContext
                .getSharedPreferences("client_profile_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }

    fun saveUser(
        context: Context,
        userId: String,
        name: String,
        emailOrPhone: String,
        role: String,
        authProvider: String? = null,
        profileImageUrl: String? = null
    ) {
        val editor = prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, emailOrPhone)
            .putString(KEY_USER_ROLE, role)
        if (authProvider != null) editor.putString(KEY_AUTH_PROVIDER, authProvider)
        if (profileImageUrl != null) editor.putString(KEY_PROFILE_IMAGE_URL, profileImageUrl)
        editor.apply()
    }

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "") ?: ""

    fun getUserName(context: Context): String =
        prefs(context).getString(KEY_USER_NAME, "User") ?: "User"

    fun getUserEmail(context: Context): String =
        prefs(context).getString(KEY_USER_EMAIL, "") ?: ""

    fun getUserRole(context: Context): String =
        prefs(context).getString(KEY_USER_ROLE, "") ?: ""

    fun getAuthProvider(context: Context): String =
        prefs(context).getString(KEY_AUTH_PROVIDER, "LOCAL") ?: "LOCAL"

    fun getProfileImageUrl(context: Context): String =
        prefs(context).getString(KEY_PROFILE_IMAGE_URL, "") ?: ""

    fun clearUser(context: Context) {
        prefs(context).edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ROLE)
            .remove(KEY_AUTH_PROVIDER)
            .remove(KEY_PROFILE_IMAGE_URL)
            .apply()
    }

    /**
     * Confirms that the locally cached session still maps to a record in the
     * database. A network/server failure is not treated as logout; only a 404
     * or malformed local session invalidates credentials.
     */
    suspend fun validateWithServer(context: Context): ValidationResult = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) return@withContext ValidationResult.INVALID

        val userId = getUserId(context)
        val path = when (getUserRole(context).uppercase()) {
            "CLIENT" -> "client"
            "LAWYER_FRESHER" -> "lawyer/fresher"
            "LAWYER_EXPERIENCED" -> "lawyer/experienced"
            else -> return@withContext ValidationResult.INVALID
        }
        if (userId.isBlank()) return@withContext ValidationResult.INVALID

        try {
            val connection = URL("${ApiConfig.BASE_URL}/api/users/$path/$userId")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("Accept", "application/json")

            try {
                when (connection.responseCode) {
                    in 200..299 -> ValidationResult.VALID
                    HttpURLConnection.HTTP_NOT_FOUND,
                    HttpURLConnection.HTTP_GONE -> ValidationResult.INVALID
                    else -> ValidationResult.UNAVAILABLE
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            ValidationResult.UNAVAILABLE
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
