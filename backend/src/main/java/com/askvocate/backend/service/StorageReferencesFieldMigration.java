package com.askvocate.backend.service;

import com.mongodb.client.result.UpdateResult;
import com.askvocate.backend.model.UserDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Retires the legacy {@code cloudinaryReferences} key on the {@code documents} collection.
 *
 * <p>{@link UserDocument#getStorageReferences()} is persisted under this field's former
 * name on every document written before the storage backend was moved to S3. The Java
 * field name is the stored name here, so the key has to be moved to keep those documents'
 * image references readable. Runs once per start-up and matches nothing afterwards.
 */
@Component
public class StorageReferencesFieldMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageReferencesFieldMigration.class);
    private static final String COLLECTION = "documents";
    private static final String LEGACY_FIELD = "cloudinaryReferences";
    private static final String CURRENT_FIELD = "storageReferences";

    private final MongoTemplate mongoTemplate;

    public StorageReferencesFieldMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        UpdateResult result = mongoTemplate.updateMulti(
                Query.query(Criteria.where(LEGACY_FIELD).exists(true)),
                new Update().rename(LEGACY_FIELD, CURRENT_FIELD),
                COLLECTION);

        if (result.getModifiedCount() > 0) {
            log.info("Migrated {} document(s): '{}' -> '{}'",
                    result.getModifiedCount(), LEGACY_FIELD, CURRENT_FIELD);
        }
    }
}
