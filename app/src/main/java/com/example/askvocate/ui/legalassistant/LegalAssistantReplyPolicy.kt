package com.example.askvocate.ui.legalassistant

internal object LegalAssistantReplyPolicy {
    private val declinedMatching = Regex(
        "\\b(?:do not|don't|don’t|stop|no need to)\\b[^.!?\\n]{0,60}" +
            "\\b(?:find|show|recommend|suggest|connect|hire)\\b[^.!?\\n]{0,60}" +
            "\\b(?:lawyers?|advocates?|attorneys?)\\b", RegexOption.IGNORE_CASE
    )
    private val lawyerRequest = Regex(
        "\\b(?:find|show|recommend|suggest|connect|hire|looking for|need|want)\\b" +
            "[^.!?\\n]{0,100}\\b(?:lawyers?|advocates?|attorneys?|vakil|vakeel)\\b" +
            "|\\b(?:lawyers?|advocates?|attorneys?)\\s+(?:recommendations?|suggestions?)\\b" +
            "|\\b(?:vakil|vakeel)\\b[^.!?\\n]{0,60}\\b(?:batao|dikhao|chahiye|dhundo)\\b" +
            "|(?:वकील|अधिवक्ता)[^.!?\\n]{0,60}(?:दिखा|बताओ|चाहिए|ढूंढ|खोज)",
        RegexOption.IGNORE_CASE
    )
    private val matchingButton = Regex(
        "\\b(?:tap|click|press|use)\\b[^.!?\\n]{0,60}\\bbutton\\b" +
            "[^.!?\\n]{0,100}\\b(?:lawyers?|advocates?|matching)\\b" +
            "|\\b(?:tap|click|press|use)\\b[^.!?\\n]{0,60}\\bfind matching lawyers\\b",
        RegexOption.IGNORE_CASE
    )
    private val trailingDisclaimer = Regex(
        "(?:\\s*(?:I(?: am|['’]m) not a lawyer[.!]?|" +
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
