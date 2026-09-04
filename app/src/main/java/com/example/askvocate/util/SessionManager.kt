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

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
