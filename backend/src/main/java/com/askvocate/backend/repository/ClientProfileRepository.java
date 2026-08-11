package com.askvocate.backend.repository;

import com.askvocate.backend.model.ClientProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientProfileRepository extends MongoRepository<ClientProfile, String> {

    @Query("{ 'emailOrPhone': ?0 }")
    Optional<ClientProfile> findByEmailOrPhone(String emailOrPhone);

    @Query(value = "{ 'emailOrPhone': ?0 }", exists = true)
    boolean existsByEmailOrPhone(String emailOrPhone);
}
