package com.askvocate.backend.controller;

import com.askvocate.backend.service.AppSessionService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** App access to the same Mongo collections used by the web frontend. */
@RestController
@RequestMapping("/api")
public class SearchCollectionsController {
    private final MongoTemplate mongo;
    private final AppSessionService sessions;

    public SearchCollectionsController(MongoTemplate mongo, AppSessionService sessions) {
        this.mongo = mongo;
        this.sessions = sessions;
    }

    private MongoCollection<Document> history() { return mongo.getDb().getCollection("search_history"); }
    private MongoCollection<Document> saved() { return mongo.getDb().getCollection("saved_searches"); }

    private String user(String authorization) { return sessions.requireClient(authorization); }

    private ObjectId id(String raw) {
        if (!ObjectId.isValid(raw)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new ObjectId(raw);
    }

    private Document owned(MongoCollection<Document> collection, String rawId, String userId) {
        Document item = collection.find(Filters.and(Filters.eq("_id", id(rawId)), Filters.eq("userId", userId))).first();
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return item;
    }

    private Document publicItem(Document item) {
        Document copy = new Document(item);
        copy.put("id", copy.remove("_id").toString());
        return copy;
    }

    private String required(String raw, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 8 || value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid case description");
        }
        return value;
    }

    private List<Document> lawyerSnapshots(List<Map<String, Object>> raw) {
        List<Document> result = new ArrayList<>();
        if (raw == null) return result;
        if (raw.size() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many advocates");
        for (Map<String, Object> item : raw) {
            Document snapshot = new Document();
            for (String field : List.of("id", "advocate_name", "practice_area_primary", "city",
                    "experience_level", "profile_rating", "consultation_fee_inr")) {
                Object value = item.get(field);
                if (value instanceof String text) snapshot.put(field, text.substring(0, Math.min(200, text.length())));
                else if (value instanceof Number number) snapshot.put(field, number);
            }
            result.add(snapshot);
        }
        return result;
    }

    public record HistoryCreate(String query, String detectedDomain, String classificationReason,
                                boolean needsClarification, String clarificationQuestion,
                                List<Map<String, Object>> lawyers, String clientEntryId, Long clientCreatedAt) {}

    @PostMapping("/search-history")
    public Document addHistory(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestBody HistoryCreate payload) {
        String userId = user(authorization);
        String clientEntryId = safe(payload.clientEntryId(), 80);
        if (!clientEntryId.isEmpty()) {
            Document existing = history().find(Filters.and(Filters.eq("userId", userId),
                    Filters.eq("clientEntryId", clientEntryId))).first();
            if (existing != null) return publicItem(existing);
        }
        long now = System.currentTimeMillis();
        long createdAt = payload.clientCreatedAt() != null && payload.clientCreatedAt() > 1_577_836_800_000L
                && payload.clientCreatedAt() <= now ? payload.clientCreatedAt() : now;
        Document item = new Document("userId", userId)
                .append("query", required(payload.query(), 2000))
                .append("detectedDomain", safe(payload.detectedDomain(), 100))
                .append("classificationReason", safe(payload.classificationReason(), 1000))
                .append("needsClarification", payload.needsClarification())
                .append("clarificationQuestion", safe(payload.clarificationQuestion(), 500))
                .append("lawyers", lawyerSnapshots(payload.lawyers()))
                .append("createdAt", new Date(createdAt));
        if (!clientEntryId.isEmpty()) item.append("clientEntryId", clientEntryId);
        history().insertOne(item);
        return publicItem(item);
    }

    private String safe(String raw, int max) {
        String value = raw == null ? "" : raw.trim();
        return value.substring(0, Math.min(max, value.length()));
    }

    @GetMapping("/search-history")
    public Map<String, Object> listHistory(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = user(authorization);
        List<Document> items = new ArrayList<>();
        history().find(Filters.eq("userId", userId)).sort(Sorts.descending("createdAt"))
                .limit(100).forEach(item -> items.add(publicItem(item)));
        return Map.of("items", items, "total", items.size());
    }

    @DeleteMapping("/search-history/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHistory(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @PathVariable String id) {
        String userId = user(authorization);
        if (history().deleteOne(Filters.and(Filters.eq("_id", id(id)), Filters.eq("userId", userId))).getDeletedCount() == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public record SaveChoice(String historyId) {}

    @PostMapping("/saved-searches")
    public Document saveChoice(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestBody SaveChoice payload) {
        String userId = user(authorization);
        Document source = owned(history(), payload.historyId(), userId);
        Document existing = saved().find(Filters.and(Filters.eq("userId", userId),
                Filters.eq("historyId", payload.historyId()))).first();
        if (existing != null) return publicItem(existing);
        String query = source.getString("query");
        String domain = source.getString("detectedDomain");
        Document item = new Document("userId", userId)
                .append("historyId", payload.historyId())
                .append("name", safe(query, 120))
                .append("query", query)
                .append("practiceArea", safe(domain, 100))
                .append("filters", new Document())
                .append("detectedDomain", domain)
                .append("lawyers", source.get("lawyers"))
                .append("createdAt", Date.from(Instant.now()));
        saved().insertOne(item);
        return publicItem(item);
    }

    @GetMapping("/saved-searches")
    public Map<String, Object> listSaved(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = user(authorization);
        List<Document> items = new ArrayList<>();
        saved().find(Filters.eq("userId", userId)).sort(Sorts.descending("createdAt"))
                .forEach(item -> items.add(publicItem(item)));
        return Map.of("items", items, "total", items.size());
    }

    @DeleteMapping("/saved-searches/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSaved(@RequestHeader(value = "Authorization", required = false) String authorization,
                            @PathVariable String id) {
        String userId = user(authorization);
        if (saved().deleteOne(Filters.and(Filters.eq("_id", id(id)), Filters.eq("userId", userId))).getDeletedCount() == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
