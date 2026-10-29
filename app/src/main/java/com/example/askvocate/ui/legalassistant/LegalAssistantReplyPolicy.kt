package com.example.askvocate.ui.legalassistant

internal object LegalAssistantReplyPolicy {
    private val declinedMatching = Regex(
        "\\b(?:do not|don't|don't|stop|no need to)\\b[^.!?\\n]{0,60}" +
            "\\b(?:find|show|recommend|suggest|connect|hire|get|talk|speak)\\b[^.!?\\n]{0,60}" +
            "\\b(?:lawyers?|advocates?|attorneys?)\\b", RegexOption.IGNORE_CASE
    )
    private val lawyerRequest = Regex(
        // English: "find/show/need/want/get/talk to/speak to/help from a lawyer"
        "\\b(?:find|show|recommend|suggest|connect|hire|get|talk|speak|looking for|need|want)\\b" +
            "[^.!?\\n]{0,100}\\b(?:lawyers?|advocates?|attorneys?|vakil|vakeel)\\b" +
            "|\\b(?:lawyers?|advocates?|attorneys?)\\s+(?:recommendations?|suggestions?)\\b" +
            // Hindi romanised
            "|\\b(?:vakil|vakeel)\\b[^.!?\\n]{0,60}\\b(?:batao|dikhao|chahiye|dhundo|chahta|chahti)\\b" +
            // Devanagari
            "|(?:वकील|अधिवक्ता)[^.!?\\n]{0,60}(?:दिखा|बताओ|चाहिए|ढूंढ|खोज|चाहता|चाहती)",
        RegexOption.IGNORE_CASE
    )
    private val matchingButton = Regex(
        // AI says "tap/click/press the button … lawyers/matching"
        "\\b(?:tap|click|press|use)\\b[^.!?\\n]{0,60}\\bbutton\\b" +
            "[^.!?\\n]{0,100}\\b(?:lawyers?|advocates?|matching)\\b" +
            "|\\b(?:tap|click|press|use)\\b[^.!?\\n]{0,60}\\bfind matching lawyers\\b" +
            // AI says "connect you with a lawyer / preparing to connect / help you find a lawyer"
            "|\\b(?:connect(?:ing)?|connecting you|prepare|preparing)\\b[^.!?\\n]{0,80}\\b(?:lawyers?|advocates?|attorneys?)\\b" +
            "|\\b(?:help(?:ing)? you (?:find|connect|reach|get))\\b[^.!?\\n]{0,80}\\b(?:lawyers?|advocates?|attorneys?)\\b" +
            "|\\b(?:match(?:ing)? you|matched you)\\b[^.!?\\n]{0,80}\\b(?:lawyers?|advocates?|attorneys?)\\b",
        RegexOption.IGNORE_CASE
    )
    private val trailingDisclaimer = Regex(
        "(?:\\s*(?:I(?: am|['']m) not a lawyer[.!]?|" +
            "This is general (?:legal )?information(?: only)?[,; .–—-]+(?:and )?not legal advice[.!]?|" +
            "This is not legal advice[.!]?))+$",
        RegexOption.IGNORE_CASE
    )

    fun cleanReply(text: String): String = text.replace(trailingDisclaimer, "").trim()

    fun matchingQuery(
        status: String,
        requested: Boolean,
        query: String,
        summary: String,
        reply: String,
        latestUserMessage: String,
        userContext: String
    ): String {
        if (status == "out_of_scope") return ""
        val available = status == "ready" || requested || query.isNotBlank() ||
            (lawyerRequest.containsMatchIn(latestUserMessage) &&
                !declinedMatching.containsMatchIn(latestUserMessage)) || matchingButton.containsMatchIn(reply)
        if (!available) return ""
        return query.trim().ifBlank { summary.trim() }.ifBlank { userContext.trim() }.take(2000)
    }
}
