package com.askvocate.backend.service;

import com.askvocate.backend.dto.BarCouncilVerificationRequest;
import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.dto.LawyerVerificationSummaryResponse;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.model.UserDocument;
import com.askvocate.backend.model.VerificationStatus;
import com.askvocate.backend.repository.ClientProfileRepository;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import com.askvocate.backend.repository.UserDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentVerificationServiceTest {

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private OcrExtractionService ocrExtractionService;

    @Spy
    private BarCouncilValidationService barCouncilValidationService = new BarCouncilValidationService();

    @Mock
    private UserDocumentRepository documentRepository;

    @Mock
    private LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository;

    @Mock
    private LawyerFresherProfileRepository lawyerFresherProfileRepository;

    @Mock
    private ClientProfileRepository clientProfileRepository;

    @InjectMocks
    private DocumentVerificationService documentVerificationService;

    private LawyerExperiencedProfile mockExperiencedLawyer;

    @BeforeEach
    void setUp() {
        mockExperiencedLawyer = new LawyerExperiencedProfile();
        mockExperiencedLawyer.setId("lawyer-123");
        mockExperiencedLawyer.setName("Adv. Rajesh Sharma");
        mockExperiencedLawyer.setEmail("rajesh.sharma@example.com");
        mockExperiencedLawyer.setVerificationStatus(Verification_Status.PENDING);
    }

    @Test
    @DisplayName("Should successfully verify lawyer direct Bar Council enrollment and update profile")
    void testVerifyBarCouncilDirectSuccess() {
        when(documentRepository.findByUserIdAndDocumentType("lawyer-123", DocumentType.BAR_COUNCIL_ID))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any(UserDocument.class)))
                .thenAnswer(invocation -> {
                    UserDocument doc = invocation.getArgument(0);
                    doc.setId("doc-1");
                    return doc;
                });
        when(lawyerExperiencedProfileRepository.findById("lawyer-123"))
                .thenReturn(Optional.of(mockExperiencedLawyer));
        when(lawyerExperiencedProfileRepository.save(any(LawyerExperiencedProfile.class)))
                .thenReturn(mockExperiencedLawyer);

        BarCouncilVerificationRequest request = new BarCouncilVerificationRequest();
        request.setUserId("lawyer-123");
        request.setBarCouncilNumber("MAH/5678/2019");
        request.setAdvocateName("Rajesh Sharma");

        DocumentVerificationResponse response = documentVerificationService.verifyBarCouncilDirect(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.VERIFIED, response.getVerificationStatus());
        assertEquals(DocumentType.BAR_COUNCIL_ID, response.getDocumentType());
        assertEquals("PENDING", response.getLawyerVerificationStatus());
        assertEquals(Boolean.TRUE, response.getNameMatched());
        verify(lawyerExperiencedProfileRepository).save(mockExperiencedLawyer);
        assertEquals("MAH/5678/2019", mockExperiencedLawyer.getBarCouncilId());
        assertEquals(Verification_Status.PENDING, mockExperiencedLawyer.getVerificationStatus());
    }

    @Test
    @DisplayName("Should generate accurate verification summary and completion percentage")
    void testGetLawyerVerificationSummary() {
        mockExperiencedLawyer.setVerificationStatus(Verification_Status.VERIFIED);
        mockExperiencedLawyer.setBarCouncilId("D/1234/2021");

        when(lawyerExperiencedProfileRepository.findById("lawyer-123"))
                .thenReturn(Optional.of(mockExperiencedLawyer));

        UserDocument barDoc = new UserDocument();
        barDoc.setId("doc-bar");
        barDoc.setUserId("lawyer-123");
        barDoc.setDocumentType(DocumentType.BAR_COUNCIL_ID);
        barDoc.setVerificationStatus(VerificationStatus.VERIFIED);

        UserDocument aadharDoc = new UserDocument();
        aadharDoc.setId("doc-aadhaar");
        aadharDoc.setUserId("lawyer-123");
        aadharDoc.setDocumentType(DocumentType.AADHAAR);
        aadharDoc.setVerificationStatus(VerificationStatus.VERIFIED);

        UserDocument panDoc = new UserDocument();
        panDoc.setId("doc-pan");
        panDoc.setUserId("lawyer-123");
        panDoc.setDocumentType(DocumentType.PAN);
        panDoc.setVerificationStatus(VerificationStatus.VERIFIED);

        when(documentRepository.findByUserId("lawyer-123"))
                .thenReturn(List.of(barDoc, aadharDoc, panDoc));

        LawyerVerificationSummaryResponse summary = documentVerificationService.getLawyerVerificationSummary("lawyer-123");

        assertNotNull(summary);
        assertEquals("lawyer-123", summary.getUserId());
        assertEquals("Adv. Rajesh Sharma", summary.getLawyerName());
        assertEquals(100, summary.getCompletionPercentage());
        assertTrue(summary.isBarCouncilVerified());
        assertTrue(summary.isAadhaarVerified());
        assertTrue(summary.isPanVerified());
        assertTrue(summary.isIdentityVerified());
        assertEquals(3, summary.getVerifiedDocuments().size());
    }
}
