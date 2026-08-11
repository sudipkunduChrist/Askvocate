package com.askvocate.backend.repository;

import com.askvocate.backend.model.LawyerFresherProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LawyerFresherProfileRepository extends MongoRepository<LawyerFresherProfile, String> {

    @Query("{ 'emailOrPhone': ?0 }")
    Optional<LawyerFresherProfile> findByEmailOrPhone(String emailOrPhone);

    @Query(value = "{ 'emailOrPhone': ?0 }", exists = true)
    boolean existsByEmailOrPhone(String emailOrPhone);
}
