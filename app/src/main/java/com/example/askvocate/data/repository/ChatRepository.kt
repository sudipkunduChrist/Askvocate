package com.example.askvocate.data.repository

import com.example.askvocate.data.model.ChatConversation
import com.example.askvocate.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatRepository {

    fun getConversations(): List<ChatConversation> {
        return listOf(
            ChatConversation(
                id = "chat_1",
                lawyerName = "Adv. Anjali Sharma",
                lastMessage = "I have reviewed the documents you sent.",
                timestamp = "10:30 AM",
                unreadCount = 2,
                isOnline = true
            ),
            ChatConversation(
                id = "chat_2",
                lawyerName = "Adv. Rajesh Kumar",
                lastMessage = "Yes, we can schedule a call tomorrow.",
                timestamp = "Yesterday",
                unreadCount = 0,
                isOnline = false
            ),
            ChatConversation(
                id = "chat_3",
                lawyerName = "Adv. Priya Singh",
                lastMessage = "Please bring the original copies to the meeting.",
                timestamp = "Oct 12",
                unreadCount = 0,
                isOnline = true
            )
        )
    }

    fun getMessagesForConversation(chatId: String): List<ChatMessage> {
        return listOf(
            ChatMessage(
                id = "msg_1",
                text = "Hello, I wanted to discuss the new contract.",
                timestamp = "10:00 AM",
                isSent = true,
                senderName = "Me"
            ),
            ChatMessage(
                id = "msg_2",
                text = "Hi! Sure, I can help with that. Have you emailed it to me?",
                timestamp = "10:05 AM",
                isSent = false,
                senderName = "Lawyer"
            ),
            ChatMessage(
                id = "msg_3",
                text = "Yes, I will send over the files shortly.",
                timestamp = "10:15 AM",
                isSent = true,
                senderName = "Me"
            ),
            ChatMessage(
                id = "msg_4",
                text = "I have reviewed the documents you sent.",
                timestamp = "10:30 AM",
                isSent = false,
                senderName = "Lawyer"
            )
        )
    }
}
