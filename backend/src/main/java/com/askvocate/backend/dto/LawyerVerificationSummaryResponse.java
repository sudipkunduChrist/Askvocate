package com.askvocate.backend.dto;

import com.askvocate.backend.entity.Verification_Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Summary DTO displaying a lawyer's complete document verification state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawyerVerificationSummaryResponse {
    private String userId;
    private String lawyerName;
    private String email;
    private String role;
    private Verification_Status verificationStatus;
    private String barCouncilId;
    private int completionPercentage;
    private boolean barCouncilVerified;
    private boolean aadhaarVerified;
    private boolean panVerified;
    private boolean identityVerified;
    private String nextStep;
    private List<DocumentVerificationResponse> verifiedDocuments;
    private Map<String, Object> summaryDetails;
}
