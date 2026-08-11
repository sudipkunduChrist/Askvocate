package com.askvocate.backend.model;

import com.askvocate.backend.entity.Role;
import com.askvocate.backend.entity.Verification_Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for LAWYER_FRESHER role users (collection: "lawyers_fresher").
 * Populated from LawyerFresherSignup DTO on registration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("lawyers_fresher")
public class LawyerFresherProfile {

    @Id
    private String id;

    /** Always Role.LAWYER_FRESHER */
    @Builder.Default
    private Role role = Role.LAWYER_FRESHER;

    private String name;

    @Indexed(unique = true)
    private String emailOrPhone;

    private String passwordHash;

    // --- Fresher-specific fields (from LawyerFresherSignup DTO) ---
    private String university;
    private Integer graduationYear;
    private String specialization;

    // --- Verification ---
    @Builder.Default
    private Verification_Status verificationStatus = Verification_Status.PENDING;

    private String rejectionReason;
    private Long verifiedAt;

    @Builder.Default
    private Long createdAt = Instant.now().toEpochMilli();
}
