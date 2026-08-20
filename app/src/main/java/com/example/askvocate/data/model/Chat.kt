package com.example.askvocate.data.model

data class ChatConversation(
    val id: String,
    val lawyerName: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false
)

data class ChatMessage(
    val id: String,
    val text: String,
    val timestamp: String,
    val isSent: Boolean,
    val senderName: String
)
