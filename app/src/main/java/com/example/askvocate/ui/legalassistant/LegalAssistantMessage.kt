package com.example.askvocate.ui.legalassistant

import com.example.askvocate.data.repository.LawyerMatchingRepository

data class LegalAssistantMessage(
    val id: String,
    val text: String,
    val timestamp: String,
    val isUser: Boolean,
    val recommendationQuery: String = "",
    val isMatching: Boolean = false,
    val matchingResult: LawyerMatchingRepository.Result? = null,
    val matchingError: String? = null
) {
    fun matchingQueryFor(clarificationAnswer: String? = null): String? {
        if (isUser || isMatching || matchingResult is LawyerMatchingRepository.Result.Success) return null
        if (clarificationAnswer == null) return recommendationQuery
        val clarification = matchingResult as? LawyerMatchingRepository.Result.Clarification ?: return null
        val answer = clarificationAnswer.trim()
        if (answer.length < 2) return null
        val suffix = "\nClarification: ${answer.take(1000)}"
        return clarification.originalQuery.take(2000 - suffix.length) + suffix
    }
}
