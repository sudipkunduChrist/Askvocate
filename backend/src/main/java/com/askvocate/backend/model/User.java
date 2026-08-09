package com.askvocate.backend.model;

import com.askvocate.backend.entity.Role;
import com.askvocate.backend.entity.Verification_Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("users")
public class User {
    @Id
    private String id;
    private String name;
    private String email;
    private String passwordHash;
    private Role role;
    private Verification_Status verificationStatus;
    private String rejectionReason;

    @Builder.Default
    private Long createdAt = Instant.now().toEpochMilli();

    private Long verifiedAt;
}
