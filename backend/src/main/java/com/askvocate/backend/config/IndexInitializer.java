package com.askvocate.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Ensures the compound lookup indexes used by the document and selfie flows.
 * Index creation is best-effort so restrictive database roles cannot stop startup.
 */
@Component
public class IndexInitializer {
    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

    private final MongoTemplate mongo;

    public IndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @PostConstruct
    void ensureIndexes() {
        // One email per role collection (cross-role uniqueness is enforced by EmailAccountService).
        ensure("clients", "email_unique", new Index().on("email", Sort.Direction.ASC).unique().sparse());
        ensure("lawyers_fresher", "email_unique", new Index().on("email", Sort.Direction.ASC).unique().sparse());
        ensure("lawyers_experienced", "email_unique", new Index().on("email", Sort.Direction.ASC).unique().sparse());
        // Duplicate-document lookups.
        ensure("documents", "pan_number_hash", new Index().on("panNumberHash", Sort.Direction.ASC));
        ensure("documents", "enrollment_number_hash", new Index().on("enrollmentNumberHash", Sort.Direction.ASC));
        // Document and selfie lookups.
        ensure("documents", "user_document_type",
                new Index().on("userId", Sort.Direction.ASC).on("documentType", Sort.Direction.ASC));
        ensure("documents", "user_doc_type",
                new Index().on("userId", Sort.Direction.ASC).on("docType", Sort.Direction.ASC));
        ensure("selfie_verifications", "selfie_challenge",
                new Index().on("userId", Sort.Direction.ASC).on("challengeId", Sort.Direction.ASC));
        ensure("selfie_verifications", "selfie_aadhaar",
                new Index().on("userId", Sort.Direction.ASC)
                        .on("aadhaarDocumentId", Sort.Direction.ASC)
                        .on("aadhaarSubmissionToken", Sort.Direction.ASC)
                        .on("verificationStatus", Sort.Direction.ASC));
    }

    private void ensure(String collection, String name, Index index) {
        try {
            mongo.indexOps(collection).ensureIndex(index.named(name));
        } catch (Exception error) {
            log.warn("Could not ensure index {} on {}: {}", name, collection,
                    error.getClass().getSimpleName());
        }
    }
}
