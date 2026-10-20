package com.askvocate.backend.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Removes legacy raw OCR text from Aadhaar documents in MongoDB. */
@Component
public class AadhaarOcrTextCleanup implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    public AadhaarOcrTextCleanup(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Query aadhaarWithOcrText = Query.query(Criteria.where("documentType").is("AADHAAR")
                .and("ocrText").exists(true));
        mongoTemplate.updateMulti(aadhaarWithOcrText, new Update().unset("ocrText"), "documents");
    }
}
