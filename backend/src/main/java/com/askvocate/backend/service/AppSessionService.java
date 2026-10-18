package com.askvocate.backend.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/** Opaque app tokens keep collection access tied to an authenticated account. */
@Service
public class AppSessionService {
    private final MongoTemplate mongo;
    private final SecureRandom random = new SecureRandom();

    public AppSessionService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private MongoCollection<Document> sessions() {
        return mongo.getDb().getCollection("app_sessions");
    }

    public String issue(String userId, String role) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions().insertOne(new Document("tokenDigest", digest(token))
                .append("userId", userId)
                .append("role", role)
                .append("expiresAt", Date.from(Instant.now().plusSeconds(30L * 24 * 60 * 60))));
        return token;
    }

    public String requireClient(String authorization) {
        Account account = requireAccount(authorization);
        if (!"CLIENT".equals(account.role())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Client account required");
        }
        return account.userId();
    }

    public Account requireAdmin(String authorization) {
        Account account = requireAccount(authorization);
        if (!"ADMIN".equals(account.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin account required");
        }
        return account;
    }

    public record Account(String userId, String role) {}

    public Account requireAccount(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        Document session = sessions().find(Filters.and(
                Filters.eq("tokenDigest", digest(token)),
                Filters.gt("expiresAt", new Date())
        )).first();
        if (session == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Sign in again.");
        String role = session.getString("role");
        if (!("CLIENT".equals(role) || "LAWYER_FRESHER".equals(role)
                || "LAWYER_EXPERIENCED".equals(role) || "ADMIN".equals(role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account type is not supported");
        }
        return new Account(session.getString("userId"), role);
    }

    private String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception error) {
            throw new IllegalStateException("Could not create session", error);
        }
    }
}
