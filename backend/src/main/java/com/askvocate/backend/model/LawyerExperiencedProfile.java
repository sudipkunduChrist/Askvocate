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
import java.util.List;

/**
 * MongoDB document for LAWYER_EXPERIENCED role users (collection: "lawyers_experienced").
 * Populated from LawyerExperiencedSignup DTO on registration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("lawyers_experienced")
public class LawyerExperiencedProfile {

    @Id
    private String id;

    /** Always Role.LAWYER_EXPERIENCED */
    @Builder.Default
    private Role role = Role.LAWYER_EXPERIENCED;

    private String name;

    @Indexed(unique = true)
    private String emailOrPhone;

    private String passwordHash;

    @Builder.Default
    private String university = "";
    @Builder.Default
    private Integer graduationYear = 0;
    @Builder.Default
    private String specialization = "";

    @Builder.Default
    private String barCouncilId = "";
    @Builder.Default
    private List<String> practiceAreas = new java.util.ArrayList<>();
    @Builder.Default
    private String currentFirm = "";

    // --- Verification ---
    @Builder.Default
    private Verification_Status verificationStatus = Verification_Status.PENDING;

    private String rejectionReason;
    private Long verifiedAt;

    @Builder.Default
    private String createdAt = Instant.now().toString();
}
