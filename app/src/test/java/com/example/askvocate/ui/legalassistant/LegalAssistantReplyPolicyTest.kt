package com.example.askvocate.ui.legalassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class LegalAssistantReplyPolicyTest {
    private val case = "My university withheld my original documents in Delhi."

    private fun query(
        reply: String = "I can help.",
        latest: String = "What should I do?",
        providedQuery: String = "",
        status: String = "gathering"
    ) = LegalAssistantReplyPolicy.matchingQuery(
        status, false, providedQuery, "", reply, latest, case
    )

    @Test fun screenshotReplyHasMatchingButtonWithoutActionFields() {
        assertEquals(case, query(
            reply = "You can now tap the button to find lawyers through Askvocate’s own advocate matching screen."
        ))
        assertEquals(case, query(reply = "Tap Find matching lawyers to see advocates directly in this chat."))
    }

    @Test fun explicitRequestUsesCaseContextWithoutActionFields() {
        assertEquals(case, query(latest = "Show lawyers for this case."))
        assertEquals(case, query(latest = "Mere case ke liye vakil batao"))
        assertEquals(case, query(latest = "इस मामले के लिए वकील दिखाओ।"))
    }

    @Test fun providedQueryWorksWithoutReadyStatus() {
        assertEquals("Document dispute in Delhi.", query(providedQuery = "Document dispute in Delhi."))
    }

    @Test fun ordinaryConversationAndDeclinedMatchingDoNotOfferButton() {
        assertEquals("", query())
        assertEquals("", query(latest = "Don't recommend lawyers yet."))
        assertEquals("", query(status = "out_of_scope", providedQuery = "Unrelated query"))
    }

    @Test fun removesOnlyTrailingBoilerplateFromScreenshot() {
        val reply = "Keep your documents and receipts."
        assertEquals(reply, LegalAssistantReplyPolicy.cleanReply(
            "$reply\n\nI am not a lawyer. This is general information, not legal advice."
        ))
        val substantive = "This is general information about collecting evidence. Keep your receipts."
        assertEquals(substantive, LegalAssistantReplyPolicy.cleanReply(substantive))
    }
}
