package com.example.askvocate.data.repository

import android.content.Context
import com.example.askvocate.data.model.SavedAdvocate
import com.example.askvocate.data.model.SavedSearch
import com.example.askvocate.data.model.SearchHistoryEntry
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.util.SessionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes the Mongo collections shared with the web app. Call on Dispatchers.IO. */
class SearchHistoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPrefs = appContext.getSharedPreferences("askvocate_search_history", Context.MODE_PRIVATE)

    private fun migrateLegacyHistory() {
        val userId = SessionManager.getUserId(appContext)
        if (userId.isBlank()) return
        val key = "history_$userId"
        val json = legacyPrefs.getString(key, null) ?: return
        val type = object : TypeToken<List<SearchHistoryEntry>>() {}.type
        val entries: List<SearchHistoryEntry> = Gson().fromJson(json, type) ?: emptyList()
        for (entry in entries.asReversed()) {
            val lawyers = JSONArray()
            entry.lawyers.forEach { advocate -> lawyers.put(JSONObject().apply {
                put("id", advocate.lawyerId)
                put("advocate_name", advocate.name)
                put("practice_area_primary", advocate.specialty)
                put("city", advocate.location)
                put("experience_level", "${advocate.yearsExperience} years")
                put("profile_rating", advocate.rating)
                put("consultation_fee_inr", advocate.consultationFee)
            }) }
            request("POST", "/search-history", JSONObject().apply {
                put("query", entry.query)
                put("detectedDomain", entry.detectedDomain)
                put("classificationReason", entry.classificationReason)
                put("needsClarification", entry.needsClarification)
                put("clarificationQuestion", entry.clarificationQuestion)
                put("lawyers", lawyers)
                put("clientEntryId", entry.id)
                put("clientCreatedAt", entry.createdAt)
            })
        }
        legacyPrefs.edit().remove(key).apply()
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val token = SessionManager.getAppToken(appContext)
        if (token.isBlank()) throw IllegalStateException("Sign in again to sync your searches.")
        val connection = (URL("${ApiConfig.BASE_URL}/api$path").openConnection() as HttpURLConnection).apply {
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
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                if (status == 401) throw IllegalStateException("Session expired. Sign in again to sync your searches.")
                throw IllegalStateException("Search sync failed (HTTP $status). Please try again.")
            }
            if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    fun add(query: String, recommendation: JSONObject) {
        migrateLegacyHistory()
        val lawyers = JSONArray()
        recommendation.optJSONArray("recommended_lawyers")?.let { source ->
            for (index in 0 until source.length()) {
                val raw = source.optJSONObject(index) ?: continue
                lawyers.put(JSONObject().apply {
                    for (field in listOf("id", "advocate_name", "practice_area_primary", "city",
                        "experience_level", "profile_rating", "consultation_fee_inr")) {
                        if (raw.has(field)) put(field, raw.get(field))
                    }
                })
            }
        }
        request("POST", "/search-history", JSONObject().apply {
            put("query", query.trim())
            put("detectedDomain", recommendation.optString("detected_domain", "Legal"))
            put("classificationReason", recommendation.optString("classification_reason"))
            put("needsClarification", recommendation.optBoolean("needs_clarification"))
            put("clarificationQuestion", recommendation.optString("clarification_question"))
            put("lawyers", lawyers)
        })
    }

    fun list(): List<SearchHistoryEntry> {
        migrateLegacyHistory()
        val items = request("GET", "/search-history").optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { item -> SearchHistoryEntry(
                id = item.optString("id"),
                query = item.optString("query"),
                detectedDomain = item.optString("detectedDomain"),
                classificationReason = item.optString("classificationReason"),
                needsClarification = item.optBoolean("needsClarification"),
                clarificationQuestion = item.optString("clarificationQuestion"),
                lawyers = parseLawyers(item.optJSONArray("lawyers")),
                createdAt = timestamp(item.opt("createdAt"))
            ) }
        }
    }

    fun delete(id: String) { request("DELETE", "/search-history/$id") }

    fun saveChoice(historyId: String) { request("POST", "/saved-searches", JSONObject().put("historyId", historyId)) }

    fun listSaved(): List<SavedSearch> {
        val items = request("GET", "/saved-searches").optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { item -> SavedSearch(
                id = item.optString("id"),
                detectedDomain = item.optString("detectedDomain", item.optString("practiceArea")),
                searchQuery = item.optString("query"),
                savedTimestamp = timestamp(item.opt("createdAt")),
                advocates = parseLawyers(item.optJSONArray("lawyers"))
            ) }
        }
    }

    fun savedHistoryIds(): Set<String> {
        val items = request("GET", "/saved-searches").optJSONArray("items") ?: return emptySet()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optString("historyId") }
            .filter { it.isNotBlank() }.toSet()
    }

    fun deleteSaved(id: String) { request("DELETE", "/saved-searches/$id") }

    private fun timestamp(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
        else -> 0L
    }

    private fun parseLawyers(items: JSONArray?): List<SavedAdvocate> {
        if (items == null) return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { item -> SavedAdvocate(
                lawyerId = item.optString("id"),
                name = item.optString("advocate_name", item.optString("name", "Advocate")),
                specialty = item.optString("practice_area_primary", item.optString("specialization", "Legal practice")),
                location = item.optString("city", item.optString("location", "")),
                rating = item.optDouble("profile_rating", item.optDouble("rating", 0.0)),
                yearsExperience = Regex("\\d+").find(item.optString("experience_level"))?.value?.toIntOrNull() ?: 0,
                consultationFee = item.optInt("consultation_fee_inr", 0),
                isVerified = true
            ) }
        }
    }
}
