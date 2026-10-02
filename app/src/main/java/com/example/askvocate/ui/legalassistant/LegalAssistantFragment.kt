package com.example.askvocate.ui.legalassistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.SavedAdvocate
import com.example.askvocate.data.repository.LawyerMatchingRepository
import com.example.askvocate.data.repository.SavedAdvocatesRepository
import com.example.askvocate.util.applyStatusBarInset

class LegalAssistantFragment : Fragment() {

    private val viewModel: LegalAssistantViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_legal_assistant, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        view.findViewById<View>(R.id.appbar).applyStatusBarInset()
        view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val list = view.findViewById<RecyclerView>(R.id.rv_assistant_messages)
        val input = view.findViewById<EditText>(R.id.et_assistant_message)
        val send = view.findViewById<ImageView>(R.id.btn_assistant_send)
        val loading = view.findViewById<ProgressBar>(R.id.assistant_loading)
        val errorContainer = view.findViewById<View>(R.id.assistant_error_container)
        val errorText = view.findViewById<TextView>(R.id.tv_assistant_error)
        view.findViewById<View>(R.id.btn_assistant_retry).setOnClickListener { viewModel.retry() }
        val inputContainer = view.findViewById<LinearLayout>(R.id.assistant_input_container)
        val adapter = LegalAssistantMessageAdapter(
            onFindAdvocates = { messageId -> viewModel.findLawyers(messageId) },
            onClarification = { messageId, answer -> viewModel.findLawyers(messageId, answer) },
            onLawyerClick = { lawyer ->
                findNavController().navigate(
                    R.id.action_legal_assistant_to_lawyer_profile,
                    Bundle().apply { putString("lawyerId", lawyer.id) }
                )
            }
        )
        list.adapter = adapter

        val inputPadding = inputContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(inputContainer) { container, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            container.updatePadding(bottom = inputPadding + maxOf(imeBottom, navigationBottom))
            insets
        }
        ViewCompat.requestApplyInsets(inputContainer)

        fun submit() {
            val message = input.text.toString()
            if (message.trim().length < 3 || viewModel.uiState.value?.isLoading == true) return
            input.text.clear()
            viewModel.send(message)
        }

        send.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else false
        }

        val savedSearches = SavedAdvocatesRepository(requireContext())
        val savedMessageIds = mutableSetOf<String>()
        var previousMessages = emptyList<LegalAssistantMessage>()
        viewModel.uiState.observe(viewLifecycleOwner) { ui ->
            val matchingIndex = ui.messages.indexOfFirst { message ->
                val previous = previousMessages.find { it.id == message.id }
                previous != null && (message.isMatching != previous.isMatching ||
                    message.matchingResult != previous.matchingResult || message.matchingError != previous.matchingError)
            }
            previousMessages = ui.messages
            adapter.submitList(ui.messages) {
                if (ui.messages.isNotEmpty()) {
                    list.scrollToPosition(if (matchingIndex >= 0) matchingIndex else ui.messages.lastIndex)
                }
            }
            ui.messages.forEach { message ->
                val result = message.matchingResult as? LawyerMatchingRepository.Result.Success
                if (result != null && savedMessageIds.add(message.id)) {
                    savedSearches.saveSearch(
                        domain = result.detectedDomain,
                        query = message.recommendationQuery,
                        advocates = result.lawyers.map { lawyer ->
                            SavedAdvocate(
                                lawyerId = lawyer.id, name = lawyer.name, specialty = lawyer.specialty,
                                location = lawyer.location, rating = lawyer.rating,
                                yearsExperience = lawyer.yearsExperience, consultationFee = lawyer.consultationFee,
                                isVerified = lawyer.isVerified
                            )
                        }
                    )
                }
            }
            loading.isVisible = ui.isLoading
            send.isEnabled = !ui.isLoading
            errorContainer.isVisible = ui.error != null && !ui.isLoading
            errorText.text = ui.error
        }
    }
}
