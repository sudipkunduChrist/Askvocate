package com.example.askvocate.util

import android.content.Context

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

    private const val PREFS_NAME = "askvocate_session"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ROLE = "user_role"

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
        if (!loggedIn) clearUser(context)
    }

    fun saveUser(
        context: Context,
        userId: String,
        name: String,
        emailOrPhone: String,
        role: String
    ) {
        prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, emailOrPhone)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "") ?: ""

    fun getUserName(context: Context): String =
        prefs(context).getString(KEY_USER_NAME, "User") ?: "User"

    fun getUserEmail(context: Context): String =
        prefs(context).getString(KEY_USER_EMAIL, "") ?: ""

    fun getUserRole(context: Context): String =
        prefs(context).getString(KEY_USER_ROLE, "") ?: ""

    fun clearUser(context: Context) {
        prefs(context).edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ROLE)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
