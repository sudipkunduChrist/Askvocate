package com.example.askvocate.ui.legalassistant

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.data.repository.LawyerMatchingRepository
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LegalAssistantViewModel : ViewModel() {

    data class UiState(
        val messages: List<LegalAssistantMessage>,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private data class Turn(val role: String, val content: String)

    private val turns = mutableListOf<Turn>()
    private val matchingRepository = LawyerMatchingRepository()
    private val _uiState = MutableLiveData(
        UiState(
            messages = listOf(
                LegalAssistantMessage(
                    id = "welcome",
                    text = "Tell me what happened in your own words. I’ll ask a few focused questions, explain practical next steps, and help you decide what kind of advocate may be useful. Please don’t share passwords, Aadhaar numbers, bank details, or exact document numbers.",
                    timestamp = now(),
                    isUser = false
                )
            )
        )
    )
    val uiState: LiveData<UiState> = _uiState

    fun send(text: String) {
        val content = text.trim()
        if (content.length < 3 || _uiState.value?.isLoading == true) return

        turns += Turn("user", content)
        val current = _uiState.value ?: return
        _uiState.value = current.copy(
            messages = current.messages + displayMessage(content, true),
            isLoading = true,
            error = null
        )

        loadReply()
    }

    fun retry() {
        val current = _uiState.value ?: return
        if (current.isLoading || current.error == null || turns.lastOrNull()?.role != "user") return
        _uiState.value = current.copy(isLoading = true, error = null)
        loadReply()
    }

    fun findLawyers(messageId: String, clarificationAnswer: String? = null) {
        val message = _uiState.value?.messages?.find { it.id == messageId } ?: return
        val query = message.matchingQueryFor(clarificationAnswer) ?: return
        if (query.trim().length < 8) {
            updateMatchingMessage(messageId) {
                it.copy(matchingError = "Please describe the legal issue in a little more detail.")
            }
            return
        }
        updateMatchingMessage(messageId) {
            it.copy(isMatching = true, recommendationQuery = query, matchingError = null)
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { matchingRepository.match(query) }
                updateMatchingMessage(messageId) {
                    it.copy(isMatching = false, matchingResult = result, matchingError = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateMatchingMessage(messageId) {
                    it.copy(
                        isMatching = false,
                        matchingError = matchingRepository.formatUserFriendlyError(error.message.orEmpty())
                    )
                }
            }
        }
    }

    private fun updateMatchingMessage(id: String, update: (LegalAssistantMessage) -> LegalAssistantMessage) {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(messages = current.messages.map { if (it.id == id) update(it) else it })
    }

    private fun loadReply() {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { requestReply() }
                val reply = buildString {
                    append(LegalAssistantReplyPolicy.cleanReply(response.getString("reply")))
                    val question = response.optString("follow_up_question").trim()
                    if (question.isNotEmpty() && !contains(question)) append("\n\n").append(question)
                    val actions = response.optJSONArray("suggested_actions")
                    if (actions != null && actions.length() > 0) {
                        append("\n\nSuggested next steps:")
                        for (index in 0 until actions.length()) append("\n• ").append(actions.optString(index))
                    }
                }
                val userTurns = turns.filter { it.role == "user" }
                val recommendationQuery = LegalAssistantReplyPolicy.matchingQuery(
                    status = response.optString("status"),
                    requested = response.optBoolean("lawyer_matching_requested"),
                    query = response.optString("recommendation_query"),
                    summary = response.optString("case_summary"),
                    reply = reply,
                    latestUserMessage = userTurns.lastOrNull()?.content.orEmpty(),
                    userContext = userTurns.joinToString("\n") { it.content }
                )
                turns += Turn("assistant", reply)
                val latest = _uiState.value ?: return@launch
                _uiState.value = latest.copy(
                    messages = latest.messages + displayMessage(
                        text = reply,
                        sent = false,
                        recommendationQuery = recommendationQuery
                    ),
                    isLoading = false,
                    error = null
                )
            } catch (error: Exception) {
                val latest = _uiState.value ?: return@launch
                _uiState.value = latest.copy(
                    isLoading = false,
                    error = if (error is SocketTimeoutException) {
                        "The legal assistant took too long to respond. Your conversation is still available. Please retry."
                    } else error.message ?: "The legal guide is unavailable. Please try again."
                )
            }
        }
    }

    private fun requestReply(): JSONObject {
        val connection = (URL("${ApiConfig.AI_BASE_URL}/legal-assistant/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            // Allow the service's 60-second provider timeout plus connection overhead.
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        return try {
            val messages = JSONArray()
            turns.takeLast(12).forEach { turn ->
                messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
            connection.outputStream.use { it.write(JSONObject().put("messages", messages).toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Legal guide request failed ($code)")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun displayMessage(
        text: String,
        sent: Boolean,
        recommendationQuery: String = ""
    ) = LegalAssistantMessage(
        id = "${System.nanoTime()}-${if (sent) "user" else "assistant"}",
        text = text,
        timestamp = now(),
        isUser = sent,
        recommendationQuery = recommendationQuery
    )

    companion object {
        private fun now(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }
}
