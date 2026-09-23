package com.example.askvocate.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.repository.ChatRepository
import com.example.askvocate.ui.adapters.ChatMessageAdapter
import com.example.askvocate.util.applyStatusBarInset

class ChatDetailFragment : Fragment() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chatId = arguments?.getString("chatId") ?: return

        view.findViewById<View>(R.id.appbar).applyStatusBarInset()

        ChatRepository().getConversations().firstOrNull { it.id == chatId }?.let { chat ->
            view.findViewById<TextView>(R.id.tv_chat_name).text = chat.lawyerName
            view.findViewById<TextView>(R.id.tv_chat_status).apply {
                text = if (chat.isOnline) getString(R.string.online) else getString(R.string.offline)
                setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        requireContext(),
                        if (chat.isOnline) R.color.online_green else R.color.text_secondary
                    )
                )
            }
            val avatar = when {
                chat.lawyerName.contains("Anjali", ignoreCase = true) -> R.drawable.pfp_female_1
                chat.lawyerName.contains("Rajesh", ignoreCase = true) -> R.drawable.pfp_male_1
                chat.lawyerName.contains("Priya", ignoreCase = true) -> R.drawable.pfp_female_2
                else -> R.drawable.ic_profile
            }
            view.findViewById<ImageView>(R.id.iv_chat_avatar).setImageResource(avatar)
        }

        view.findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val rvMessages = view.findViewById<RecyclerView>(R.id.rv_messages)
        val messageAdapter = ChatMessageAdapter()
        rvMessages.adapter = messageAdapter

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            messageAdapter.submitList(messages) {
                rvMessages.scrollToPosition(messages.size - 1)
            }
        }

        val etMessage = view.findViewById<EditText>(R.id.et_message)
        view.findViewById<ImageView>(R.id.btn_send).setOnClickListener {
            val text = etMessage.text.toString()
            if (text.isNotBlank()) {
                viewModel.sendMessage(chatId, text)
                etMessage.text.clear()
            }
        }

        viewModel.loadMessages(chatId)
    }
}
