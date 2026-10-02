package com.example.askvocate.ui.findlawyers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.repository.LawyerMatchingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FindLawyersViewModel : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Success(val detectedDomain: String, val lawyers: List<Lawyer>) : UiState()
        data class Clarification(val question: String, val originalQuery: String) : UiState()
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

    fun submitClarification(answerText: String) {
        val clarification = _uiState.value as? UiState.Clarification ?: return
        val answer = answerText.trim()
        if (answer.length < 2) {
            _uiState.value = UiState.Error("Please answer the clarification question before continuing.")
            return
        }

        val expandedQuery = buildString {
            append(clarification.originalQuery)
            append("\nClarification: ")
            append(answer)
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                withContext(Dispatchers.IO) { requestRecommendations(expandedQuery) }
            } catch (error: Exception) {
                UiState.Error(formatUserFriendlyError(error.message ?: error.toString()))
            }
        }
    }

    private val matchingRepository = LawyerMatchingRepository()

    private fun formatUserFriendlyError(message: String): String =
        matchingRepository.formatUserFriendlyError(message)

    private fun requestRecommendations(query: String): UiState =
        when (val result = matchingRepository.match(query)) {
            is LawyerMatchingRepository.Result.Success -> UiState.Success(result.detectedDomain, result.lawyers)
            is LawyerMatchingRepository.Result.Clarification -> UiState.Clarification(result.question, result.originalQuery)
        }
}
