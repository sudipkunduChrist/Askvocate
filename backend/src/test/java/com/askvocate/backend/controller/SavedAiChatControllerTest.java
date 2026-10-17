package com.askvocate.backend.controller;

import com.askvocate.backend.service.AppSessionService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class SavedAiChatControllerTest {
    @Test
    void createsAccountOwnedConversationWithCompleteMessages() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase database = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked") MongoCollection<Document> chats = mock(MongoCollection.class);
        AppSessionService sessions = mock(AppSessionService.class);
        when(mongo.getDb()).thenReturn(database);
        when(database.getCollection("saved_ai_chat")).thenReturn(chats);
        when(sessions.requireClient("Bearer app-token")).thenReturn("user-123");
        doAnswer(call -> {
            Document item = call.getArgument(0);
            item.put("_id", new ObjectId());
            return null;
        }).when(chats).insertOne(any(Document.class));

        Document result = new SavedAiChatController(mongo, sessions).create("Bearer app-token",
                new SavedAiChatController.ChatSnapshot(List.of(
                        Map.of("id", "welcome", "role", "assistant", "content", "Welcome"),
                        Map.of("id", "user-1", "role", "user", "content", "My landlord kept my deposit"),
                        Map.of("id", "assistant-1", "role", "assistant", "content", "Keep the receipts",
                                "recommendationQuery", "tenant deposit", "matchingError", "Please retry"))));

        assertNotNull(result.getString("id"));
        assertEquals("user-123", result.getString("userId"));
        assertEquals("Keep the receipts", result.getString("title"));
        assertEquals(3, result.getList("messages", Document.class).size());
        assertEquals("Please retry", result.getList("messages", Document.class).get(2).getString("matchingError"));
        verify(chats).insertOne(any(Document.class));
    }

    @Test
    void titleTracksLatestMessageAndSkipsAutomaticWelcome() {
        List<Document> messages = List.of(
                new Document("id", "welcome").append("role", "assistant").append("content", "Welcome"),
                new Document("role", "user").append("content", "My landlord kept my deposit"));
        assertEquals("My landlord kept my deposit", SavedAiChatController.title(messages));

        List<Document> withReply = new java.util.ArrayList<>(messages);
        withReply.add(new Document("role", "assistant").append("content", "Keep the receipts.\nAsk for a written response."));
        assertEquals("Keep the receipts. Ask for a written response.", SavedAiChatController.title(withReply));

        withReply.add(new Document("role", "user").append("content", "What if they refuse?"));
        assertEquals("What if they refuse?", SavedAiChatController.title(withReply));
    }

    @Test
    void updatesExistingConversationInsteadOfCreatingOnePerReply() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase database = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked") MongoCollection<Document> chats = mock(MongoCollection.class);
        @SuppressWarnings("unchecked") FindIterable<Document> found = mock(FindIterable.class);
        AppSessionService sessions = mock(AppSessionService.class);
        ObjectId id = new ObjectId();
        when(mongo.getDb()).thenReturn(database);
        when(database.getCollection("saved_ai_chat")).thenReturn(chats);
        when(sessions.requireClient("Bearer app-token")).thenReturn("user-123");
        when(chats.find(any(Bson.class))).thenReturn(found);
        when(found.first()).thenReturn(new Document("_id", id).append("userId", "user-123")
                .append("messages", List.of(new Document("role", "user"))));

        Document result = new SavedAiChatController(mongo, sessions).update("Bearer app-token", id.toString(),
                new SavedAiChatController.ChatSnapshot(List.of(
                        Map.of("role", "user", "content", "My landlord kept my deposit"),
                        Map.of("role", "assistant", "content", "Keep the receipts"))));

        assertEquals(id.toString(), result.getString("id"));
        verify(chats).updateOne(any(Bson.class), any(Bson.class));
        verify(chats, never()).insertOne(any(Document.class));
    }
}
