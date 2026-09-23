package com.askvocate.backend.repository;

import com.askvocate.backend.model.LawyerExperiencedProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LawyerExperiencedProfileRepository extends MongoRepository<LawyerExperiencedProfile, String> {

    @Query("{ '$or': [ { 'email': ?0 }, { 'emailOrPhone': ?0 } ] }")
    Optional<LawyerExperiencedProfile> findByEmail(String email);

    @Query(value = "{ '$or': [ { 'email': ?0 }, { 'emailOrPhone': ?0 } ] }", exists = true)
    boolean existsByEmail(String email);
}
