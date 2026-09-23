package com.example.askvocate.ui.findlawyers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.network.ApiConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FindLawyersViewModel : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Success(val detectedDomain: String, val lawyers: List<Lawyer>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    fun findMatchingLawyers(caseDescription: String) {
        val query = caseDescription.trim()
        if (query.length < 8) {
            _uiState.value = UiState.Error("Please describe the legal issue in a little more detail.")
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withContext(Dispatchers.IO) { requestRecommendations(query) }
            } catch (error: Exception) {
                UiState.Error(formatUserFriendlyError(error.message ?: error.toString()))
            }
        }
    }

    private fun formatUserFriendlyError(rawMessage: String): String {
        val message = rawMessage.trim()
        if (message.isEmpty()) {
            return "The lawyer matching service is temporarily unavailable. Please try again in a moment."
        }

        val normalized = message.lowercase()
        return when {
            normalized.contains("timed out") || normalized.contains("timeout") ||
                normalized.contains("connect") || normalized.contains("unreachable") ||
                normalized.contains("unknownhost") || normalized.contains("refused") ->
                "The AI matching service is currently unavailable. Please try again in a moment."

            normalized.contains("internal server error") || normalized.contains("server error") ||
                normalized.contains("exception") || normalized.contains("traceback") ||
                normalized.contains("failed to connect") || normalized.contains("500") ->
                "The AI matching service is temporarily unavailable. Please try again later."

            normalized.contains("invalid response") || normalized.contains("json") ||
                normalized.contains("parse") ->
                "The AI matching service returned an unexpected response. Please try again."

            normalized.contains("not found") ->
                "We couldn't find a matching lawyer for that description. Please try a different legal issue."

            else -> message
        }
    }

    private fun requestRecommendations(query: String): UiState.Success {
        val connection = (URL("${ApiConfig.AI_BASE_URL}/recommend").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val body = JSONObject().put("query", query).put("top_k", 10).toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = responseBody.takeIf { it.isNotBlank() }?.let(::JSONObject)

            if (statusCode !in 200..299) {
                val detail = json?.optString("detail")?.takeIf { it.isNotBlank() }
                val rawMessage = detail ?: "Matching failed (HTTP $statusCode)."
                throw IllegalStateException(formatUserFriendlyError(rawMessage))
            }

            val recommendations = json?.optJSONArray("recommended_lawyers")
                ?: throw IllegalStateException(formatUserFriendlyError("The matching service returned an invalid response."))
            val lawyers = buildList {
                for (index in 0 until recommendations.length()) {
                    val item = recommendations.optJSONObject(index) ?: continue
                    val experience = item.optString("experience_level")
                    val years = Regex("\\d+").find(experience)?.value?.toIntOrNull() ?: 0
                    add(Lawyer(
                        id = item.optString("id", "ai-$index"),
                        name = item.optString("advocate_name", "Advocate"),
                        specialty = item.optString("practice_area_primary", "Legal practice"),
                        rating = item.optDouble("profile_rating", 0.0),
                        reviewCount = 0,
                        location = item.optString("city", "Location not listed"),
                        bio = item.optString("practice_area_secondary", ""),
                        yearsExperience = years,
                        isVerified = true,
                        consultationFee = item.optInt("consultation_fee_inr", 0)
                    ))
                }
            }

            UiState.Success(
                detectedDomain = json.optString("detected_domain", "Recommended advocates"),
                lawyers = lawyers
            )
        } finally {
            connection.disconnect()
        }
    }
}
