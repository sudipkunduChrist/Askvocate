package com.askvocate.backend.model;

import com.askvocate.backend.entity.Role;
import com.askvocate.backend.entity.Verification_Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class User {
    private String id;                                    // Firebase Auth UID
    private String name;
    private String email;
    private Role role;                                    // CLIENT, LAWYER_FRESHER, LAWYER_EXPERIENCED, ADMIN
    private Verification_Status verificationStatus;       // PENDING, UNDER_VERIFICATION, VERIFIED, REJECTED
    private String rejectionReason;                       // Set if verification fails
    private Long createdAt;
    private Long verifiedAt;
}
