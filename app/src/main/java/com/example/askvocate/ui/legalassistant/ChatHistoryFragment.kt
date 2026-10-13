package com.example.askvocate.ui.legalassistant

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.MainActivity
import com.example.askvocate.R
import com.example.askvocate.data.repository.SavedAiChatRepository
import com.example.askvocate.util.applyStatusBarInset
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatHistoryFragment : Fragment(R.layout.fragment_chat_history) {
    private lateinit var repository: SavedAiChatRepository
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var status: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SavedAiChatRepository(requireContext())
        view.findViewById<View>(R.id.chat_history_app_bar).applyStatusBarInset()
        view.findViewById<ImageButton>(R.id.btn_chat_history_menu).setOnClickListener {
            (requireActivity() as? MainActivity)?.openDrawer()
        }
        view.findViewById<View>(R.id.btn_new_guide_chat).setOnClickListener {
            repository.setActiveChatId(null)
            findNavController().navigate(R.id.nav_legal_assistant)
        }
        list = view.findViewById(R.id.rv_chat_history)
        empty = view.findViewById(R.id.chat_history_empty)
        status = view.findViewById(R.id.tv_chat_history_status)
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) refresh()
    }

    private fun refresh() {
        status.isVisible = true
        status.setText(R.string.chat_history_loading)
        list.isVisible = false
        empty.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.list() } }
                .onSuccess { chats ->
                    status.isVisible = false
                    list.isVisible = chats.isNotEmpty()
                    empty.isVisible = chats.isEmpty()
                    list.adapter = ChatAdapter(chats,
                        onOpen = { id -> findNavController().navigate(R.id.nav_legal_assistant,
                            Bundle().apply { putString("chatId", id) }) },
                        onDelete = { id ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                runCatching { withContext(Dispatchers.IO) { repository.delete(id) } }
                                    .onSuccess { refresh() }
                                    .onFailure { Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show() }
                            }
                        })
                }
                .onFailure { status.text = it.message ?: getString(R.string.chat_history_load_failed) }
        }
    }

    private class ChatAdapter(
        private val chats: List<SavedAiChatRepository.ChatSummary>,
        private val onOpen: (String) -> Unit,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<ChatAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_chat_history, parent, false))
        override fun getItemCount(): Int = chats.size
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(chats[position])

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView = view.findViewById(R.id.tv_chat_history_title)
            private val detail: TextView = view.findViewById(R.id.tv_chat_history_detail)
            private val delete: ImageButton = view.findViewById(R.id.btn_delete_chat)
            fun bind(chat: SavedAiChatRepository.ChatSummary) {
                title.text = chat.title
                val context = itemView.context
                detail.text = context.resources.getQuantityString(R.plurals.chat_history_messages,
                    chat.messageCount, chat.messageCount) + " · " +
                    DateFormat.getMediumDateFormat(context).format(Date(chat.updatedAt))
                itemView.setOnClickListener { onOpen(chat.id) }
                delete.setOnClickListener { onDelete(chat.id) }
            }
        }
    }
}
