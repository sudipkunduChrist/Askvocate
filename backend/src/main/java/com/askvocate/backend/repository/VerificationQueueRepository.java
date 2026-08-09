package com.askvocate.backend.repository;

import com.askvocate.backend.model.VerificationQueueItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationQueueRepository extends MongoRepository<VerificationQueueItem, String> {
}
