package com.askvocate.backend.repository;

import com.askvocate.backend.entity.DocType;
import com.askvocate.backend.model.UserDoc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDocRepository extends MongoRepository<UserDoc, String> {

    // Get all documents for a user
    List<UserDoc> findByUserId(String userId);

    // Get specific document
    Optional<UserDoc> findByUserIdAndDocType(String userId, DocType docType);
}