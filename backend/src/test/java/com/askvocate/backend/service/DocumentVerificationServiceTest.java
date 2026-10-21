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
import com.askvocate.backend.repository.SelfieVerificationRepository;
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

    @Mock
    private VisionVerificationClient visionVerificationClient;

    @Mock
    private SelfieVerificationRepository selfieVerificationRepository;

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
    @DisplayName("Bar Council number format alone is sent to manual review")
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
        assertEquals(VerificationStatus.PENDING, response.getVerificationStatus());
        assertEquals(DocumentType.BAR_COUNCIL_ID, response.getDocumentType());
        assertEquals("PENDING", response.getLawyerVerificationStatus());
        assertEquals(Boolean.TRUE, response.getNameMatched());
        assertTrue(response.getRequiresManualReview());
        assertEquals("BAR_REVIEW", response.getPendingReason());
        verify(lawyerExperiencedProfileRepository).save(mockExperiencedLawyer);
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
        aadharDoc.setIsQrVerified(true);
        aadharDoc.setQrPrintedMismatch(false);
        aadharDoc.setSubmissionToken("aadhaar-revision");

        UserDocument panDoc = new UserDocument();
        panDoc.setId("doc-pan");
        panDoc.setUserId("lawyer-123");
        panDoc.setDocumentType(DocumentType.PAN);
        panDoc.setVerificationStatus(VerificationStatus.VERIFIED);

        UserDocument copDoc = new UserDocument();
        copDoc.setId("doc-cop");
        copDoc.setUserId("lawyer-123");
        copDoc.setDocumentType(DocumentType.CERTIFICATE_OF_PRACTICE);
        copDoc.setVerificationStatus(VerificationStatus.VERIFIED);

        UserDocument aibeDoc = new UserDocument();
        aibeDoc.setId("doc-aibe");
        aibeDoc.setUserId("lawyer-123");
        aibeDoc.setDocumentType(DocumentType.AIBE_CERTIFICATE);
        aibeDoc.setVerificationStatus(VerificationStatus.VERIFIED);

        when(documentRepository.findByUserId("lawyer-123"))
                .thenReturn(List.of(barDoc, aadharDoc, panDoc, copDoc, aibeDoc));
        when(selfieVerificationRepository.existsByUserIdAndAadhaarDocumentIdAndAadhaarSubmissionTokenAndVerificationStatus(
                "lawyer-123", "doc-aadhaar", "aadhaar-revision", VerificationStatus.VERIFIED)).thenReturn(true);

        LawyerVerificationSummaryResponse summary = documentVerificationService.getLawyerVerificationSummary("lawyer-123");

        assertNotNull(summary);
        assertEquals("lawyer-123", summary.getUserId());
        assertEquals("Adv. Rajesh Sharma", summary.getLawyerName());
        assertEquals(100, summary.getCompletionPercentage());
        assertTrue(summary.isBarCouncilVerified());
        assertTrue(summary.isBarEnrollmentVerified());
        assertTrue(summary.isCopVerified());
        assertTrue(summary.isAibeVerified());
        assertFalse(summary.isDegreeVerified());
        assertTrue(summary.isAadhaarVerified());
        assertTrue(summary.isPanVerified());
        assertTrue(summary.isIdentityVerified());
        assertEquals(5, summary.getVerifiedDocuments().size());
    }

    @Test
    void partialDocumentChecklistReportsPendingEvenIfProfileWasPreviouslyMarkedVerified() {
        mockExperiencedLawyer.setVerificationStatus(Verification_Status.VERIFIED);
        mockExperiencedLawyer.setBarCouncilId("D/1234/2021");
        when(lawyerExperiencedProfileRepository.findById("lawyer-123"))
                .thenReturn(Optional.of(mockExperiencedLawyer));
        UserDocument aadhaar = new UserDocument();
        aadhaar.setDocumentType(DocumentType.AADHAAR);
        aadhaar.setVerificationStatus(VerificationStatus.VERIFIED);
        aadhaar.setIsQrVerified(true);
        aadhaar.setQrPrintedMismatch(false);
        aadhaar.setSubmissionToken("aadhaar-revision");
        when(documentRepository.findByUserId("lawyer-123")).thenReturn(List.of(aadhaar));

        LawyerVerificationSummaryResponse summary = documentVerificationService.getLawyerVerificationSummary("lawyer-123");

        assertEquals(Verification_Status.PENDING, summary.getVerificationStatus());
        assertEquals(16, summary.getCompletionPercentage());
        assertFalse(summary.isBarCouncilVerified());
    }

    @Test
    void barNumberFormatDoesNotCompleteLawyerVerification() {
        when(lawyerExperiencedProfileRepository.findById("lawyer-123"))
                .thenReturn(Optional.of(mockExperiencedLawyer));
        UserDocument aadhaar = new UserDocument();
        aadhaar.setId("aadhaar-id");
        aadhaar.setDocumentType(DocumentType.AADHAAR);
        aadhaar.setVerificationStatus(VerificationStatus.VERIFIED);
        aadhaar.setIsQrVerified(true);
        aadhaar.setQrPrintedMismatch(false);
        aadhaar.setSubmissionToken("aadhaar-revision");
        UserDocument pan = new UserDocument();
        pan.setId("pan-id");
        pan.setDocumentType(DocumentType.PAN);
        pan.setVerificationStatus(VerificationStatus.VERIFIED);
        when(documentRepository.findByUserId("lawyer-123")).thenReturn(List.of(aadhaar, pan));
        when(selfieVerificationRepository.existsByUserIdAndAadhaarDocumentIdAndAadhaarSubmissionTokenAndVerificationStatus(
                "lawyer-123", "aadhaar-id", "aadhaar-revision", VerificationStatus.VERIFIED)).thenReturn(true);
        when(documentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> {
            UserDocument doc = invocation.getArgument(0);
            doc.setId("bar-id");
            return doc;
        });
        BarCouncilVerificationRequest request = new BarCouncilVerificationRequest();
        request.setUserId("lawyer-123");
        request.setBarCouncilNumber("D/1234/2021");

        DocumentVerificationResponse response = documentVerificationService.verifyBarCouncilDirect(request);

        assertEquals(Verification_Status.PENDING, mockExperiencedLawyer.getVerificationStatus());
        assertEquals("PENDING", response.getLawyerVerificationStatus());
        assertTrue(response.getRequiresManualReview());
        assertEquals("BAR_REVIEW", response.getPendingReason());
        assertNull(mockExperiencedLawyer.getVerifiedAt());
    }
}
