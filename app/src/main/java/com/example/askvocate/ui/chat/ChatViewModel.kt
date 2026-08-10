package com.example.askvocate.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.askvocate.data.model.ChatConversation
import com.example.askvocate.data.model.ChatMessage
import com.example.askvocate.data.repository.ChatRepository

class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()

    private val _conversations = MutableLiveData<List<ChatConversation>>()
    val conversations: LiveData<List<ChatConversation>> = _conversations

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    fun loadConversations() {
        _conversations.value = repository.getConversations()
    }

    fun loadMessages(chatId: String) {
        _messages.value = repository.getMessagesForConversation(chatId)
    }

    fun sendMessage(chatId: String, text: String) {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        val newMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = text,
            timestamp = "Just now",
            isSent = true,
            senderName = "Me"
        )
        currentMessages.add(newMessage)
        _messages.value = currentMessages
    }
}
