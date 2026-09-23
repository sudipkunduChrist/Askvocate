package com.askvocate.backend.repository;

import com.askvocate.backend.model.ClientProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientProfileRepository extends MongoRepository<ClientProfile, String> {

    @Query("{ '$or': [ { 'email': ?0 }, { 'emailOrPhone': ?0 } ] }")
    Optional<ClientProfile> findByEmail(String email);

    @Query(value = "{ '$or': [ { 'email': ?0 }, { 'emailOrPhone': ?0 } ] }", exists = true)
    boolean existsByEmail(String email);
}
