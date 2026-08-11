package com.askvocate.backend.repository;

import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.model.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link UserDocument} entities.
 * 
 * <p>All queries are scoped by {@code userId} to enforce per-user data isolation.
 */
@Repository
public interface UserDocumentRepository extends MongoRepository<UserDocument, String> {

    /** Find all verification documents belonging to a user. */
    List<UserDocument> findByUserId(String userId);

    /** Find a specific document type for a user. */
    Optional<UserDocument> findByUserIdAndDocumentType(String userId, DocumentType documentType);

    /** Check whether a user already has a verification record for a given document type. */
    boolean existsByUserIdAndDocumentType(String userId, DocumentType documentType);
}
