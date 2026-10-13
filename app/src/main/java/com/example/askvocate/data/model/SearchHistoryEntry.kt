package com.example.askvocate.data.model

data class SearchHistoryEntry(
    val id: String,
    val query: String,
    val detectedDomain: String,
    val classificationReason: String,
    val needsClarification: Boolean,
    val clarificationQuestion: String,
    val lawyers: List<SavedAdvocate>,
    val createdAt: Long
)
