package com.askvocate.backend.controller;

import com.askvocate.backend.service.AppSessionService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Stores complete legal-guide conversations in the shared saved_ai_chat collection. */
@RestController
@RequestMapping("/api/ai-chats")
public class SavedAiChatController {
    private final MongoTemplate mongo;
    private final AppSessionService sessions;

    public SavedAiChatController(MongoTemplate mongo, AppSessionService sessions) {
        this.mongo = mongo;
        this.sessions = sessions;
    }

    private MongoCollection<Document> chats() { return mongo.getDb().getCollection("saved_ai_chat"); }
    private String user(String authorization) { return sessions.requireClient(authorization); }

    private ObjectId id(String value) {
        if (!ObjectId.isValid(value)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new ObjectId(value);
    }

    private Document owned(String rawId, String userId) {
        Document chat = chats().find(Filters.and(Filters.eq("_id", id(rawId)), Filters.eq("userId", userId))).first();
        if (chat == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return chat;
    }

    private Document publicItem(Document chat) {
        Document result = new Document(chat);
        result.put("id", result.remove("_id").toString());
        return result;
    }

    public record ChatSnapshot(List<Map<String, Object>> messages) {}

    private List<Document> messages(ChatSnapshot payload) {
        if (payload == null || payload.messages() == null || payload.messages().isEmpty()
                || payload.messages().size() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid chat length");
        }
        List<Document> result = new ArrayList<>();
        for (Map<String, Object> raw : payload.messages()) {
            String role = String.valueOf(raw.getOrDefault("role", ""));
            String content = String.valueOf(raw.getOrDefault("content", ""));
            if (!(role.equals("user") || role.equals("assistant")) || content.isBlank() || content.length() > 4000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid chat message");
            Document message = new Document("id", limit(raw.get("id"), 100))
                    .append("role", role)
                    .append("content", content)
                    .append("timestamp", limit(raw.get("timestamp"), 40))
                    .append("recommendationQuery", limit(raw.get("recommendationQuery"), 2000))
                    .append("matchingError", limit(raw.get("matchingError"), 1000));
            Object match = raw.get("matchingResult");
            if (match instanceof Map<?, ?> resultMap) {
                // This is display data only. Limit its serialized size and keep it private to the owner.
                String json = new Document((Map<String, Object>) resultMap).toJson();
                if (json.length() > 10_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match result too large");
                message.append("matchingResult", new Document((Map<String, Object>) resultMap));
            }
            result.add(message);
        }
        return result;
    }

    private String limit(Object value, int max) {
        String text = value == null ? "" : String.valueOf(value);
        return text.substring(0, Math.min(max, text.length()));
    }

    private String title(List<Document> messages) {
        return messages.stream().filter(message -> "user".equals(message.getString("role")))
                .map(message -> limit(message.getString("content"), 80)).findFirst().orElse("Legal guide chat");
    }

    @PostMapping
    public Document create(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @RequestBody ChatSnapshot payload) {
        String userId = user(authorization);
        List<Document> entries = messages(payload);
        Date now = Date.from(Instant.now());
        Document chat = new Document("userId", userId)
                .append("title", title(entries))
                .append("messages", entries)
                .append("createdAt", now)
                .append("updatedAt", now);
        chats().insertOne(chat);
        return publicItem(chat);
    }

    @PutMapping("/{id}")
    public Document update(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @PathVariable String id, @RequestBody ChatSnapshot payload) {
        String userId = user(authorization);
        owned(id, userId);
        List<Document> entries = messages(payload);
        chats().updateOne(Filters.and(Filters.eq("_id", id(id)), Filters.eq("userId", userId)),
                Updates.combine(Updates.set("messages", entries), Updates.set("title", title(entries)),
                        Updates.set("updatedAt", Date.from(Instant.now()))));
        return publicItem(owned(id, userId));
    }

    @GetMapping
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = user(authorization);
        List<Document> items = new ArrayList<>();
        chats().find(Filters.eq("userId", userId)).sort(Sorts.descending("updatedAt"))
                .forEach(chat -> items.add(new Document("id", chat.getObjectId("_id").toString())
                        .append("title", chat.getString("title"))
                        .append("createdAt", chat.getDate("createdAt"))
                        .append("updatedAt", chat.getDate("updatedAt"))
                        .append("messageCount", chat.getList("messages", Document.class, List.of()).size())));
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Document get(@RequestHeader(value = "Authorization", required = false) String authorization,
                        @PathVariable String id) {
        return publicItem(owned(id, user(authorization)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable String id) {
        String userId = user(authorization);
        if (chats().deleteOne(Filters.and(Filters.eq("_id", id(id)), Filters.eq("userId", userId))).getDeletedCount() == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
