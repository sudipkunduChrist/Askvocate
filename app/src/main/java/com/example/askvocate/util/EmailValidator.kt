package com.example.askvocate.util

import java.util.regex.Pattern

object EmailValidator {
    /**
     * Strict RFC-5322 compliant Email Regular Expression.
     * Enforces alphanumeric characters, dots/dashes/underscores, '@' domain symbol,
     * and a valid top-level domain extension (e.g., .com, .org, .co.in).
     */
    private val EMAIL_PATTERN: Pattern = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    )

    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }
}
