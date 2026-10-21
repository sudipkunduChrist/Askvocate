package com.askvocate.backend.controller;

import com.askvocate.backend.service.AppSessionService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.conversions.Bson;
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
import static org.mockito.Mockito.when;

class SearchCollectionsControllerTest {
    @Test
    void storesEverySuccessfulMatchInWebHistoryShapeForAuthenticatedUser() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase database = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked") MongoCollection<Document> collection = mock(MongoCollection.class);
        AppSessionService sessions = mock(AppSessionService.class);
        when(mongo.getDb()).thenReturn(database);
        when(database.getCollection("search_history")).thenReturn(collection);
        when(sessions.requireClient("Bearer app-token")).thenReturn("user-123");
        doAnswer(call -> {
            Document item = call.getArgument(0);
            item.put("_id", new ObjectId());
            return null;
        }).when(collection).insertOne(any(Document.class));

        SearchCollectionsController controller = new SearchCollectionsController(mongo, sessions);
        Document response = controller.addHistory("Bearer app-token", new SearchCollectionsController.HistoryCreate(
                "My landlord kept my deposit", "Property Law", "", false, "",
                List.of(Map.of("id", "ADV-1", "advocate_name", "Advocate One")), null, null));

        assertNotNull(response.getString("id"));
        assertEquals("user-123", response.getString("userId"));
        assertEquals("My landlord kept my deposit", response.getString("query"));
        assertEquals("Property Law", response.getString("detectedDomain"));
        assertEquals("ADV-1", ((Document) response.getList("lawyers", Document.class).getFirst()).getString("id"));
        assertNotNull(response.getDate("createdAt"));
        verify(collection).insertOne(any(Document.class));
    }

    @Test
    void copiesOnlyAChosenHistoryItemIntoSavedSearches() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase database = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked") MongoCollection<Document> history = mock(MongoCollection.class);
        @SuppressWarnings("unchecked") MongoCollection<Document> saved = mock(MongoCollection.class);
        @SuppressWarnings("unchecked") FindIterable<Document> historyFind = mock(FindIterable.class);
        @SuppressWarnings("unchecked") FindIterable<Document> savedFind = mock(FindIterable.class);
        AppSessionService sessions = mock(AppSessionService.class);
        when(mongo.getDb()).thenReturn(database);
        when(database.getCollection("search_history")).thenReturn(history);
        when(database.getCollection("saved_searches")).thenReturn(saved);
        when(sessions.requireClient("Bearer app-token")).thenReturn("user-123");
        when(history.find(any(Bson.class))).thenReturn(historyFind);
        when(saved.find(any(Bson.class))).thenReturn(savedFind);
        when(historyFind.first()).thenReturn(new Document("query", "My landlord kept my deposit")
                .append("detectedDomain", "Property Law")
                .append("lawyers", List.of(new Document("id", "ADV-1"))));
        doAnswer(call -> {
            Document item = call.getArgument(0);
            item.put("_id", new ObjectId());
            return null;
        }).when(saved).insertOne(any(Document.class));

        String historyId = new ObjectId().toString();
        Document response = new SearchCollectionsController(mongo, sessions)
                .saveChoice("Bearer app-token", new SearchCollectionsController.SaveChoice(historyId));

        assertEquals("user-123", response.getString("userId"));
        assertEquals(historyId, response.getString("historyId"));
        assertEquals("My landlord kept my deposit", response.getString("name"));
        assertEquals("Property Law", response.getString("practiceArea"));
        verify(saved).insertOne(any(Document.class));
    }
}
