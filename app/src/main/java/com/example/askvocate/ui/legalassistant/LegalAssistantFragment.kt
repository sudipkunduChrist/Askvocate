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
import com.example.askvocate.util.applyStatusBarInset

class LegalAssistantFragment : Fragment() {

    private val viewModel: LegalAssistantViewModel by viewModels()
    private var hasOpenedArgument = false

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("hasOpenedChatArgument", hasOpenedArgument)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeActiveChat()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_legal_assistant, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        hasOpenedArgument = state?.getBoolean("hasOpenedChatArgument") ?: hasOpenedArgument
        view.findViewById<View>(R.id.appbar).applyStatusBarInset()
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        toolbar.inflateMenu(R.menu.legal_assistant_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_guide_history -> {
                    findNavController().navigate(R.id.nav_chat_history)
                    true
                }
                R.id.action_guide_new -> {
                    viewModel.newChat()
                    true
                }
                else -> false
            }
        }
        if (!hasOpenedArgument) {
            arguments?.getString("chatId")?.takeIf { it.isNotBlank() }?.let(viewModel::openChat)
            hasOpenedArgument = true
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
        var previousImeBottom = 0
        ViewCompat.setOnApplyWindowInsetsListener(inputContainer) { container, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            container.updatePadding(bottom = inputPadding + maxOf(imeBottom, navigationBottom))
            if (imeBottom > 0 && imeBottom != previousImeBottom) {
                list.post {
                    if (adapter.itemCount > 0) {
                        list.scrollToPosition(adapter.itemCount - 1)
                        list.post { list.scrollBy(0, list.computeVerticalScrollRange()) }
                    }
                }
            }
            previousImeBottom = imeBottom
            insets
        }
        ViewCompat.requestApplyInsets(inputContainer)

        fun submit() {
            val message = input.text.toString()
            if (message.trim().length < 3 || viewModel.uiState.value?.isLoading == true ||
                viewModel.uiState.value?.error != null) return
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
            loading.isVisible = ui.isLoading
            send.isEnabled = !ui.isLoading && ui.error == null
            errorContainer.isVisible = ui.error != null && !ui.isLoading
            errorText.text = ui.error
        }
    }
}
