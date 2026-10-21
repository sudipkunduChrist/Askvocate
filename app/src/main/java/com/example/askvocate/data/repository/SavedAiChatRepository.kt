package com.example.askvocate.data.repository

import android.content.Context
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.ui.legalassistant.LegalAssistantMessage
import com.example.askvocate.util.SessionManager
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Account-owned legal-guide conversations stored in MongoDB saved_ai_chat. Call on Dispatchers.IO. */
class SavedAiChatRepository(context: Context) {
    data class ChatSummary(val id: String, val title: String, val updatedAt: Long, val messageCount: Int)

    private val appContext = context.applicationContext
    private val activePrefs = appContext.getSharedPreferences("active_legal_guide_chat", Context.MODE_PRIVATE)

    fun activeChatId(): String? {
        val userId = SessionManager.getUserId(appContext).takeIf { it.isNotBlank() } ?: return null
        return activePrefs.getString(userId, null)?.takeIf { it.isNotBlank() }
    }

    fun setActiveChatId(chatId: String?) {
        val userId = SessionManager.getUserId(appContext).takeIf { it.isNotBlank() } ?: return
        activePrefs.edit().apply {
            if (chatId == null) remove(userId) else putString(userId, chatId)
        }.apply()
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val token = SessionManager.getAppToken(appContext)
        if (token.isBlank()) throw IllegalStateException("Sign in again to save your legal guide chats.")
        val connection = (URL("${ApiConfig.BASE_URL}/api/ai-chats$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                if (code == 401) throw IllegalStateException("Session expired. Sign in again to save your chats.")
                throw IllegalStateException("Could not save your chat (HTTP $code). Please retry.")
            }
            if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    fun save(chatId: String?, messages: List<LegalAssistantMessage>): String {
        val entries = JSONArray()
        messages.forEach { message -> entries.put(JSONObject().apply {
            put("id", message.id)
            put("role", if (message.isUser) "user" else "assistant")
            put("content", message.text)
            put("timestamp", message.timestamp)
            put("recommendationQuery", message.recommendationQuery)
            message.matchingError?.let { put("matchingError", it) }
            message.matchingResult?.let { result -> put("matchingResult", matchingJson(result)) }
        }) }
        val response = request(if (chatId == null) "POST" else "PUT", chatId?.let { "/$it" } ?: "",
            JSONObject().put("messages", entries))
        return response.getString("id")
    }

    fun load(chatId: String): List<LegalAssistantMessage> {
        val entries = request("GET", "/$chatId").optJSONArray("messages") ?: return emptyList()
        return (0 until entries.length()).mapNotNull { index ->
            entries.optJSONObject(index)?.let { item -> LegalAssistantMessage(
                id = item.optString("id"),
                text = item.optString("content"),
                timestamp = item.optString("timestamp"),
                isUser = item.optString("role") == "user",
                recommendationQuery = item.optString("recommendationQuery"),
                matchingResult = parseMatching(item.optJSONObject("matchingResult")),
                matchingError = item.optString("matchingError").takeIf { it.isNotBlank() }
            ) }
        }
    }

    fun list(): List<ChatSummary> {
        val entries = request("GET", "").optJSONArray("items") ?: return emptyList()
        return (0 until entries.length()).mapNotNull { index ->
            entries.optJSONObject(index)?.let { item -> ChatSummary(
                id = item.optString("id"),
                title = item.optString("title"),
                updatedAt = when (val date = item.opt("updatedAt")) {
                    is Number -> date.toLong()
                    is String -> runCatching { Instant.parse(date).toEpochMilli() }.getOrDefault(0L)
                    else -> 0L
                },
                messageCount = item.optInt("messageCount")
            ) }
        }
    }

    fun delete(chatId: String) {
        request("DELETE", "/$chatId")
        if (activeChatId() == chatId) setActiveChatId(null)
    }

    private fun matchingJson(result: LawyerMatchingRepository.Result): JSONObject = when (result) {
        is LawyerMatchingRepository.Result.Clarification -> JSONObject().apply {
            put("type", "clarification")
            put("question", result.question)
            put("originalQuery", result.originalQuery)
        }
        is LawyerMatchingRepository.Result.Success -> JSONObject().apply {
            put("type", "success")
            put("detectedDomain", result.detectedDomain)
            put("lawyers", JSONArray().apply { result.lawyers.forEach { lawyer -> put(JSONObject().apply {
                put("id", lawyer.id)
                put("name", lawyer.name)
                put("specialty", lawyer.specialty)
                put("rating", lawyer.rating)
                put("reviewCount", lawyer.reviewCount)
                put("location", lawyer.location)
                put("bio", lawyer.bio)
                put("yearsExperience", lawyer.yearsExperience)
                put("isVerified", lawyer.isVerified)
                put("consultationFee", lawyer.consultationFee)
            }) } })
        }
    }

    private fun parseMatching(raw: JSONObject?): LawyerMatchingRepository.Result? = when (raw?.optString("type")) {
        "clarification" -> LawyerMatchingRepository.Result.Clarification(
            raw.optString("question"), raw.optString("originalQuery"))
        "success" -> {
            val entries = raw.optJSONArray("lawyers") ?: JSONArray()
            LawyerMatchingRepository.Result.Success(raw.optString("detectedDomain"),
                (0 until entries.length()).mapNotNull { index -> entries.optJSONObject(index)?.let { item -> Lawyer(
                    id = item.optString("id"), name = item.optString("name"),
                    specialty = item.optString("specialty"), rating = item.optDouble("rating"),
                    reviewCount = item.optInt("reviewCount"), location = item.optString("location"),
                    bio = item.optString("bio"), yearsExperience = item.optInt("yearsExperience"),
                    isVerified = item.optBoolean("isVerified"), consultationFee = item.optInt("consultationFee")
                ) } })
        }
        else -> null
    }
}
