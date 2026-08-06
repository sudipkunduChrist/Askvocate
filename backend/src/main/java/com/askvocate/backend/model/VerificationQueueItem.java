package com.askvocate.backend.model;

import com.askvocate.backend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// JSON FILE TEMPLATE FOR "VerificationQueue" COLLECTION/FOLDER IN FIREBASE.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class VerificationQueueItem {
    private String userId;
    private String userName;
    private Role role; // LAWYER_FRESHER or LAWYER_EXPERIENCED
    private Long submittedAt;
    private String status; // e.g. "UNDER_VERIFICATION"
    private int flaggedDocCount;
}

