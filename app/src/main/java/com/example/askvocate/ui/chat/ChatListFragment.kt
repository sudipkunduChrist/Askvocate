package com.example.askvocate.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.ui.adapters.ChatListAdapter
import com.example.askvocate.util.AnimationUtils

class ChatListFragment : Fragment() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvChatList = view.findViewById<RecyclerView>(R.id.rv_chat_list)
        val chatListAdapter = ChatListAdapter { chat ->
            val bundle = Bundle().apply { putString("chatId", chat.id) }
            findNavController().navigate(R.id.action_chat_list_to_chat_detail, bundle)
        }
        rvChatList.adapter = chatListAdapter

        viewModel.conversations.observe(viewLifecycleOwner) {
            chatListAdapter.submitList(it)
            AnimationUtils.runLayoutAnimation(rvChatList)
        }

        viewModel.loadConversations()
    }
}
