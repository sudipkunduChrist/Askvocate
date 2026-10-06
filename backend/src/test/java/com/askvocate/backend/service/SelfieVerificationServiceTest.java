package com.askvocate.backend.service;

import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.SelfieVerificationRepository;
import com.askvocate.backend.repository.UserDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SelfieVerificationServiceTest {
    private final DocumentVerificationService documents = mock(DocumentVerificationService.class);
    private final UserDocumentRepository documentRepository = mock(UserDocumentRepository.class);
    private final SelfieVerificationRepository selfies = mock(SelfieVerificationRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final CloudinaryService cloudinary = mock(CloudinaryService.class);
    private final VisionVerificationClient vision = mock(VisionVerificationClient.class);
    private final SelfieVerificationService service = new SelfieVerificationService(
            documents, documentRepository, selfies, mongo, cloudinary, vision);
    private final MockMultipartFile frame = new MockMultipartFile("center", "camera.jpg", "image/jpeg", new byte[]{1});

    @Test
    void pendingAadhaarCannotStartSelfieChallenge() {
        UserDocument aadhaar = new UserDocument();
        aadhaar.setVerificationStatus(VerificationStatus.PENDING);
        when(documentRepository.findByUserIdAndDocumentType("lawyer", DocumentType.AADHAAR))
                .thenReturn(Optional.of(aadhaar));
        assertThrows(DocumentVerificationException.class, () -> service.createChallenge("lawyer"));
        verifyNoInteractions(selfies, vision, cloudinary);
    }

    @Test
    void consumedOrExpiredChallengeNeverCallsFaceMatcher() {
        when(documentRepository.findByUserIdAndDocumentType("lawyer", DocumentType.AADHAAR))
                .thenReturn(Optional.of(verifiedAadhaar()));
        assertThrows(DocumentVerificationException.class,
                () -> service.verify("lawyer", "used", frame, frame, frame));
        verifyNoInteractions(vision, cloudinary);
    }

    @Test
    void successfulMatchConsumesChallengeAndStoresSeparateSelfie() throws Exception {
        when(documentRepository.findByUserIdAndDocumentType("lawyer", DocumentType.AADHAAR))
                .thenReturn(Optional.of(verifiedAadhaar()));
        SelfieVerification attempt = new SelfieVerification();
        attempt.setId("selfie-id");
        attempt.setUserId("lawyer");
        attempt.setAadhaarDocumentId("aadhaar-id");
        attempt.setAadhaarSubmissionToken("aadhaar-revision");
        attempt.setExpectedTurn("left");
        attempt.setChallengeExpiresAt(Instant.now().plusSeconds(60));
        when(mongo.findAndModify(any(), any(), eq(SelfieVerification.class))).thenReturn(attempt);
        when(cloudinary.downloadReference(any())).thenReturn(new byte[]{2});
        when(vision.verifyAadhaarQr(any(), anyMap(), nullable(String.class))).thenReturn(
                new VisionVerificationClient.QrResult(true, Map.of("name", "Test Lawyer"), true,
                        "test.cer", List.of(), false, null));
        when(vision.verifySelfie(any(), anyList(), eq("left"))).thenReturn(
                new VisionVerificationClient.SelfieResult(0.8, 0.363, true, 0.9, true, null));
        when(cloudinary.uploadSelfie(any(), anyString()))
                .thenReturn(new CloudinaryRef("selfie-public-id", "https://res.cloudinary.com/test/image", "selfie"));
        when(selfies.save(any())).thenAnswer(call -> call.getArgument(0));

        SelfieVerification saved = service.verify("lawyer", "challenge", frame, frame, frame);

        assertEquals(VerificationStatus.VERIFIED, saved.getVerificationStatus());
        assertNotNull(saved.getChallengeConsumedAt());
        assertEquals("aadhaar-id", saved.getAadhaarDocumentId());
        assertEquals("selfie-public-id", saved.getSelfiePublicId());
        verify(documents).refreshLawyerStatus("lawyer");
    }

    private UserDocument verifiedAadhaar() {
        UserDocument aadhaar = new UserDocument();
        aadhaar.setId("aadhaar-id");
        aadhaar.setDocumentType(DocumentType.AADHAAR);
        aadhaar.setVerificationStatus(VerificationStatus.VERIFIED);
        aadhaar.setSubmissionToken("aadhaar-revision");
        aadhaar.setCloudinaryReferences(List.of(new CloudinaryRef("back-id",
                "https://res.cloudinary.com/test/image", "back")));
        return aadhaar;
    }
}
