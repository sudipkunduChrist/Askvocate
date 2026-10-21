package com.example.askvocate.ui.legalassistant

import com.example.askvocate.data.repository.LawyerMatchingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalAssistantMatchingTest {
    private val query = "My landlord in Pune withheld my rental deposit."
    private val message = LegalAssistantMessage("reply", "Find lawyers in this chat.", "now", false, query)

    @Test fun inlineMatchUsesTheCurrentCaseQuery() {
        assertEquals(query, message.matchingQueryFor())
    }

    @Test fun loadingAndCompletedMessagesCannotSubmitDuplicateRequests() {
        assertNull(message.copy(isMatching = true).matchingQueryFor())
        assertNull(message.copy(matchingResult = LawyerMatchingRepository.Result.Success("Civil Law", emptyList())).matchingQueryFor())
        assertNull(message.copy(isUser = true).matchingQueryFor())
    }

    @Test fun clarificationPreservesCaseAndAddsAnswer() {
        val clarification = message.copy(matchingResult = LawyerMatchingRepository.Result.Clarification("Who withheld it?", query))
        assertEquals("$query\nClarification: My landlord", clarification.matchingQueryFor(" My landlord "))
        assertNull(clarification.matchingQueryFor(" "))
        assertNull(message.matchingQueryFor("My landlord"))
    }

    @Test fun longCaseRetainsClarificationWithinApiLimit() {
        val clarification = message.copy(matchingResult = LawyerMatchingRepository.Result.Clarification("Where?", "x".repeat(2000)))
        val expanded = clarification.matchingQueryFor("Pune")!!
        assertEquals(2000, expanded.length)
        assertTrue(expanded.endsWith("\nClarification: Pune"))
    }

    @Test fun failedRequestCanRetryWithExpandedCase() {
        val expanded = "$query\nClarification: Pune"
        assertEquals(expanded, message.copy(recommendationQuery = expanded, matchingError = "Timed out").matchingQueryFor())
    }
}
