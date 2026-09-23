package com.example.askvocate.data.model

/**
 * Represents a single saved advocate search result.
 * Contains lawyer details along with the detected legal domain and search context.
 */
data class SavedAdvocate(
    val lawyerId: String,
    val name: String,
    val specialty: String,
    val location: String,
    val rating: Double,
    val yearsExperience: Int,
    val consultationFee: Int,
    val isVerified: Boolean
)

/**
 * A grouped search result — one AI matching session produces one SavedSearch
 * containing the detected domain, original query, and all matched lawyers.
 */
data class SavedSearch(
    val id: String,
    val detectedDomain: String,
    val searchQuery: String,
    val savedTimestamp: Long,
    val advocates: List<SavedAdvocate>
)
