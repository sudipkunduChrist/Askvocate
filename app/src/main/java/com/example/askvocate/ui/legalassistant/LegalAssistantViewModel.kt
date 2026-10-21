package com.example.askvocate.ui.legalassistant

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.askvocate.network.ApiConfig
import com.example.askvocate.data.repository.LawyerMatchingRepository
import com.example.askvocate.data.repository.SavedAiChatRepository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class LegalAssistantViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val messages: List<LegalAssistantMessage>,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private data class Turn(val role: String, val content: String)

    private val turns = mutableListOf<Turn>()
    private val matchingRepository = LawyerMatchingRepository(application)
    private val chatRepository = SavedAiChatRepository(application)
    private val saveMutex = Mutex()
    private var chatId: String? = null
    private var pendingRestoreId: String? = null
    private var replyPending = false
    private val _uiState = MutableLiveData(
        UiState(
            messages = listOf(welcomeMessage())
        )
    )
    val uiState: LiveData<UiState> = _uiState

    fun resumeActiveChat() {
        val activeId = chatRepository.activeChatId()
        if (chatId == activeId) return
        if (activeId != null) openChat(activeId)
        else if (chatId != null && _uiState.value?.isLoading != true) resetChat()
    }

    fun openChat(id: String) {
        if (id.isBlank() || chatId == id || _uiState.value?.isLoading == true) return
        pendingRestoreId = id
        _uiState.value = UiState(messages = listOf(welcomeMessage()), isLoading = true)
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { chatRepository.load(id) }
                chatId = id
                chatRepository.setActiveChatId(id)
                pendingRestoreId = null
                turns.clear()
                saved.filter { it.id != "welcome" }.forEach { message ->
                    turns += Turn(if (message.isUser) "user" else "assistant", message.text)
                }
                replyPending = saved.lastOrNull()?.isUser == true
                _uiState.value = UiState(
                    messages = saved.ifEmpty { listOf(welcomeMessage()) },
                    error = if (replyPending) "The last message has no reply yet. Tap Retry." else null
                )
            } catch (error: Exception) {
                _uiState.value = UiState(listOf(welcomeMessage()), error = error.message ?: "Could not load this chat.")
            }
        }
    }

    fun newChat() {
        if (_uiState.value?.isLoading == true || _uiState.value?.error != null) return
        chatRepository.setActiveChatId(null)
        resetChat()
    }

    private fun resetChat() {
        chatId = null
        pendingRestoreId = null
        replyPending = false
        turns.clear()
        _uiState.value = UiState(messages = listOf(welcomeMessage()))
    }

    fun send(text: String) {
        val content = text.trim()
        if (content.length < 3 || content.length > 4000 || _uiState.value?.isLoading == true ||
            _uiState.value?.error != null) return

        turns += Turn("user", content)
        val current = _uiState.value ?: return
        _uiState.value = current.copy(
            messages = current.messages + displayMessage(content, true),
            isLoading = true,
            error = null
        )

        replyPending = true
        loadReply()
    }

    fun retry() {
        val current = _uiState.value ?: return
        if (current.isLoading || current.error == null) return
        pendingRestoreId?.let { openChat(it); return }
        _uiState.value = current.copy(isLoading = true, error = null)
        if (replyPending) loadReply() else viewModelScope.launch {
            try {
                persistCurrent()
                _uiState.value = _uiState.value?.copy(isLoading = false, error = null)
            } catch (error: Exception) {
                _uiState.value = _uiState.value?.copy(isLoading = false,
                    error = error.message ?: "Could not save your chat.")
            }
        }
    }

    fun findLawyers(messageId: String, clarificationAnswer: String? = null) {
        if (_uiState.value?.error != null) return
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
                val result = withContext(Dispatchers.IO) {
                    matchingRepository.match(query, recordInSearchHistory = false)
                }
                updateMatchingMessage(messageId) {
                    it.copy(isMatching = false, matchingResult = result, matchingError = null)
                }
                persistCurrent()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val current = _uiState.value ?: return@launch
                if (current.messages.any { it.id == messageId && it.matchingResult != null }) {
                    _uiState.value = current.copy(error = error.message ?: "Could not save your chat.")
                } else {
                    updateMatchingMessage(messageId) {
                        it.copy(isMatching = false,
                            matchingError = matchingRepository.formatUserFriendlyError(error.message.orEmpty()))
                    }
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
                persistCurrent()
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
                replyPending = false
                persistCurrent()
            } catch (error: CancellationException) {
                throw error
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

    private suspend fun persistCurrent() = saveMutex.withLock {
        val snapshot = _uiState.value?.messages.orEmpty()
        if (snapshot.size <= 1) return@withLock
        chatId = withContext(Dispatchers.IO) { chatRepository.save(chatId, snapshot) }
        chatRepository.setActiveChatId(chatId)
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
        private fun welcomeMessage() = LegalAssistantMessage(
            id = "welcome",
            text = "Tell me what happened in your own words. I’ll ask a few focused questions, explain practical next steps, and help you decide what kind of advocate may be useful. Please don’t share passwords, Aadhaar numbers, bank details, or exact document numbers.",
            timestamp = now(),
            isUser = false
        )
    }
}
