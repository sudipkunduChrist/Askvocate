package com.askvocate.backend.service;

import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.model.LawyerFresherProfile;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LawyerApprovalStatusTest {
    @Test
    void fresherCannotBeApprovedWithMissingDocuments() {
        var repo = mock(LawyerFresherProfileRepository.class);
        var verification = mock(DocumentVerificationService.class);
        var service = new LawyerFresherService();
        ReflectionTestUtils.setField(service, "lawyerFresherProfileRepository", repo);
        ReflectionTestUtils.setField(service, "documentVerificationService", verification);
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(repo.findById("fresher-id")).thenReturn(Optional.of(profile));

        assertThrows(IllegalArgumentException.class, () -> service.approve("fresher-id"));

        assertEquals(Verification_Status.PENDING, profile.getVerificationStatus());
        verify(repo).save(profile);
        verify(verification).areAllMandatoryDocumentsVerified("fresher-id");
    }

    @Test
    void experiencedApprovalRequiresCompleteDocumentChecklist() {
        var repo = mock(LawyerExperiencedProfileRepository.class);
        var verification = mock(DocumentVerificationService.class);
        var service = new LawyerExperiencedService();
        ReflectionTestUtils.setField(service, "lawyerExperiencedProfileRepository", repo);
        ReflectionTestUtils.setField(service, "documentVerificationService", verification);
        var profile = new LawyerExperiencedProfile();
        profile.setId("experienced-id");
        when(repo.findById("experienced-id")).thenReturn(Optional.of(profile));
        when(verification.areAllMandatoryDocumentsVerified("experienced-id")).thenReturn(true);
        when(repo.save(profile)).thenReturn(profile);

        var approved = service.approve("experienced-id");

        assertEquals(Verification_Status.VERIFIED, approved.getVerificationStatus());
        assertNotNull(approved.getVerifiedAt());
    }
}
