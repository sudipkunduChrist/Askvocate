package com.example.askvocate.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagerTest {
    @Test
    fun saveUser_andReadUserData() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        SessionManager.saveUser(
            context = context,
            userId = "u123",
            name = "Aditi Sharma",
            emailOrPhone = "aditi@example.com",
            role = "CLIENT"
        )

        assertEquals("Aditi Sharma", SessionManager.getUserName(context))
        assertEquals("aditi@example.com", SessionManager.getUserEmail(context))
        assertEquals("u123", SessionManager.getUserId(context))
        assertEquals("CLIENT", SessionManager.getUserRole(context))
    }
}
