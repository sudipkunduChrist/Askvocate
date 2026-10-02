package com.example.askvocate.ui.legalassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.repository.LawyerMatchingRepository
import com.example.askvocate.ui.adapters.LawyerAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText

class LegalAssistantMessageAdapter(
    private val onFindAdvocates: (String) -> Unit,
    private val onClarification: (String, String) -> Unit,
    private val onLawyerClick: (Lawyer) -> Unit
) : ListAdapter<LegalAssistantMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int = if (getItem(position).isUser) USER else ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == USER) {
            UserHolder(inflater.inflate(R.layout.item_legal_assistant_user, parent, false))
        } else {
            AssistantHolder(inflater.inflate(R.layout.item_legal_assistant_response, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserHolder -> holder.bind(getItem(position))
            is AssistantHolder -> holder.bind(getItem(position))
        }
    }

    private class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val message = view.findViewById<TextView>(R.id.tv_assistant_user_message)
        private val time = view.findViewById<TextView>(R.id.tv_assistant_user_time)

        fun bind(item: LegalAssistantMessage) {
            message.text = item.text
            time.text = item.timestamp
        }
    }

    private inner class AssistantHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val message = view.findViewById<TextView>(R.id.tv_assistant_response)
        private val time = view.findViewById<TextView>(R.id.tv_assistant_response_time)
        private val action = view.findViewById<MaterialButton>(R.id.btn_inline_find_advocates)
        private val loading = view.findViewById<View>(R.id.inline_matching_loading)
        private val error = view.findViewById<TextView>(R.id.inline_matching_error)
        private val results = view.findViewById<View>(R.id.inline_matching_results)
        private val domain = view.findViewById<TextView>(R.id.inline_matching_domain)
        private val count = view.findViewById<TextView>(R.id.inline_matching_count)
        private val empty = view.findViewById<View>(R.id.inline_matching_empty)
        private val lawyerList = view.findViewById<RecyclerView>(R.id.inline_matching_lawyers)
        private val lawyers = LawyerAdapter(isHorizontal = false, onItemClick = onLawyerClick)
        private val clarification = view.findViewById<View>(R.id.inline_matching_clarification)
        private val question = view.findViewById<TextView>(R.id.inline_clarification_question)
        private val answerLayout = view.findViewById<TextInputLayout>(R.id.inline_clarification_answer_layout)
        private val answer = view.findViewById<TextInputEditText>(R.id.inline_clarification_answer)
        private val continueButton = view.findViewById<MaterialButton>(R.id.inline_clarification_submit)
        private var boundId: String? = null
        private var boundQuestion: String? = null

        init { lawyerList.adapter = lawyers }

        fun bind(item: LegalAssistantMessage) {
            message.text = item.text
            time.text = item.timestamp
            val matched = item.matchingResult as? LawyerMatchingRepository.Result.Success
            val needsContext = item.matchingResult as? LawyerMatchingRepository.Result.Clarification
            action.isVisible = item.recommendationQuery.isNotBlank() && matched == null &&
                (needsContext == null || item.matchingError != null)
            action.isEnabled = !item.isMatching
            action.setText(if (item.matchingError == null) R.string.find_matching_advocates else R.string.assistant_matching_retry)
            action.setOnClickListener {
                if (!item.isMatching && item.recommendationQuery.isNotBlank()) onFindAdvocates(item.id)
            }
            loading.isVisible = item.isMatching
            error.isVisible = item.matchingError != null
            error.text = item.matchingError
            results.isVisible = matched != null
            domain.text = matched?.detectedDomain
            val total = matched?.lawyers?.size ?: 0
            count.text = itemView.resources.getQuantityString(R.plurals.matching_lawyers_count, total, total)
            empty.isVisible = matched != null && total == 0
            lawyers.submitList(matched?.lawyers.orEmpty())
            clarification.isVisible = needsContext != null
            question.text = needsContext?.question
            if (boundId != item.id || boundQuestion != needsContext?.question) {
                answer.text?.clear()
                answerLayout.error = null
            }
            boundId = item.id
            boundQuestion = needsContext?.question
            answer.isEnabled = !item.isMatching
            continueButton.isEnabled = !item.isMatching
            continueButton.setOnClickListener {
                val text = answer.text?.toString().orEmpty().trim()
                if (text.length < 2) {
                    answerLayout.error = itemView.context.getString(R.string.assistant_matching_answer_required)
                } else {
                    answerLayout.error = null
                    onClarification(item.id, text)
                }
            }
            answer.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    if (!item.isMatching) continueButton.performClick()
                    true
                } else false
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LegalAssistantMessage>() {
        override fun areItemsTheSame(oldItem: LegalAssistantMessage, newItem: LegalAssistantMessage) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LegalAssistantMessage, newItem: LegalAssistantMessage) =
            oldItem == newItem
    }

    companion object {
        private const val USER = 1
        private const val ASSISTANT = 2
    }
}
