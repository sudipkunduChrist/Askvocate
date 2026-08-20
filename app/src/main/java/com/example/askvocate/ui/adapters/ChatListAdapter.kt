package com.example.askvocate.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.askvocate.R
import com.example.askvocate.data.model.ChatConversation

class ChatListAdapter(
    private val onChatClick: (ChatConversation) -> Unit
) : ListAdapter<ChatConversation, ChatListAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_conversation, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tv_last_message)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val tvUnreadCount: TextView = itemView.findViewById(R.id.tv_unread_count)

        fun bind(chat: ChatConversation) {
            tvName.text = chat.lawyerName
            tvLastMessage.text = chat.lastMessage
            tvTime.text = chat.timestamp

            if (chat.unreadCount > 0) {
                tvUnreadCount.visibility = View.VISIBLE
                tvUnreadCount.text = chat.unreadCount.toString()
            } else {
                tvUnreadCount.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onChatClick(chat)
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatConversation>() {
        override fun areItemsTheSame(oldItem: ChatConversation, newItem: ChatConversation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatConversation, newItem: ChatConversation): Boolean {
            return oldItem == newItem
        }
    }
}
