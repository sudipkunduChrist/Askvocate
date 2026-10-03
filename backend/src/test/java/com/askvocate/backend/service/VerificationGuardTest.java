package com.askvocate.backend.service;

import com.askvocate.backend.dto.BarCouncilVerificationRequest;
import com.askvocate.backend.entity.Role;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.exception.GlobalExceptionHandler;
import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.model.LawyerFresherProfile;
import com.askvocate.backend.repository.ClientProfileRepository;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import com.askvocate.backend.repository.UserDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationGuardTest {
    private final CloudinaryService cloudinary = mock(CloudinaryService.class);
    private final UserDocumentRepository documents = mock(UserDocumentRepository.class);
    private final LawyerExperiencedProfileRepository experienced = mock(LawyerExperiencedProfileRepository.class);
    private final LawyerFresherProfileRepository freshers = mock(LawyerFresherProfileRepository.class);
    private final ClientProfileRepository clients = mock(ClientProfileRepository.class);
    private final DocumentVerificationService service = new DocumentVerificationService(cloudinary,
            new OcrExtractionService(), new BarCouncilValidationService(), documents, experienced, freshers, clients);
    private final MockMultipartFile front = new MockMultipartFile("front", "photo.jpg", "image/jpeg", new byte[]{1});

    @BeforeEach
    void resetMocks() {
        reset(cloudinary, documents, experienced, freshers, clients);
    }

    @Test
    void roleErrorsReturnHttp200WithAnExplicitFailureBody() {
        var response = new GlobalExceptionHandler().handleDocumentVerification(
                new DocumentVerificationException("Document verification is only available for lawyers."));
        assertEquals(200, response.getStatusCode().value());
        assertEquals(false, response.getBody().get("success"));
        assertEquals(200, response.getBody().get("status"));
        assertTrue(response.getBody().get("error").toString().contains("only available"));
    }

    @Test
    void unknownIdIsRejectedBeforeUploadOrDocumentCreation() {
        var error = assertThrows(DocumentVerificationException.class,
                () -> service.verifyDocument("missing-id", DocumentType.PAN, front, null));
        assertEquals("Invalid userID.", error.getMessage());
        verifyNoInteractions(cloudinary, documents);
    }

    @Test
    void clientIdCannotVerifyDocumentsOrBarNumber() {
        when(clients.existsById("client-id")).thenReturn(true);
        var error = assertThrows(DocumentVerificationException.class,
                () -> service.verifyDocument("client-id", DocumentType.PAN, front, null));
        assertTrue(error.getMessage().contains("only available for"));
        var request = new BarCouncilVerificationRequest();
        request.setUserId("client-id");
        request.setBarCouncilNumber("D/1234/2021");
        assertThrows(DocumentVerificationException.class, () -> service.verifyBarCouncilDirect(request));
        verifyNoInteractions(cloudinary, documents);
    }

    @Test
    void invalidRoleInLawyerCollectionIsRejected() {
        var profile = new LawyerExperiencedProfile();
        profile.setId("admin-id");
        profile.setRole(Role.ADMIN);
        when(experienced.findById("admin-id")).thenReturn(Optional.of(profile));
        assertThrows(DocumentVerificationException.class,
                () -> service.verifyDocument("admin-id", DocumentType.PAN, front, null));
        verifyNoInteractions(cloudinary, documents);
    }

    @Test
    void existingFresherCanReachOcrAndMissingOcrFailsWithoutInventedData() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setVerificationStatus(Verification_Status.REJECTED);
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(), Map.of()));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);

        assertEquals(com.askvocate.backend.model.VerificationStatus.FAILED, response.getVerificationStatus());
        assertTrue(response.getExtractedFields().isEmpty());
        assertNull(response.getMaskedDocumentNumber());
        assertEquals("photo.jpg", response.getFile());
        assertEquals("PENDING", response.getLawyerVerificationStatus());
        assertEquals(Verification_Status.PENDING, profile.getVerificationStatus());
        verify(documents).save(argThat(document -> document.getExtractedData().isEmpty()));
        verify(experienced, never()).save(any());
    }

    @Test
    void fullyVerifiedLawyerCannotSubmitAnotherDocumentOrBarNumber() {
        var profile = new LawyerExperiencedProfile();
        profile.setId("experienced-id");
        profile.setVerificationStatus(Verification_Status.VERIFIED);
        when(experienced.findById("experienced-id")).thenReturn(Optional.of(profile));
        when(documents.findByUserId("experienced-id")).thenReturn(List.of(
                verifiedDocument(DocumentType.AADHAAR), verifiedDocument(DocumentType.PAN),
                verifiedDocument(DocumentType.BAR_COUNCIL_ID)));

        var error = assertThrows(DocumentVerificationException.class,
                () -> service.verifyDocument("experienced-id", DocumentType.PAN, front, null));
        assertTrue(error.getMessage().contains("already complete"));
        var request = new BarCouncilVerificationRequest();
        request.setUserId("experienced-id");
        request.setBarCouncilNumber("D/1234/2021");
        assertThrows(DocumentVerificationException.class, () -> service.verifyBarCouncilDirect(request));
        verifyNoInteractions(cloudinary);
        verify(documents, never()).save(any());
    }

    private com.askvocate.backend.model.UserDocument verifiedDocument(DocumentType type) {
        var document = new com.askvocate.backend.model.UserDocument();
        document.setDocumentType(type);
        document.setVerificationStatus(com.askvocate.backend.model.VerificationStatus.VERIFIED);
        return document;
    }

    @Test
    void uploadErrorIdentifiesActualUploadedFilenameWithoutLocalPath() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var namedFile = new MockMultipartFile("front", "C:\\uploads\\aadhaar_front.jpg", "image/jpeg", new byte[]{1});
        when(cloudinary.uploadWithOcr(eq(namedFile), anyString(), eq("front")))
                .thenThrow(new java.io.IOException("Cloudinary unavailable"));

        var error = assertThrows(DocumentVerificationException.class,
                () -> service.verifyDocument("fresher-id", DocumentType.PAN, namedFile, null));
        assertEquals("aadhaar_front.jpg", error.getFile());
        var response = new GlobalExceptionHandler().handleDocumentVerification(error);
        assertEquals("aadhaar_front.jpg", response.getBody().get("file"));
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void failedBackImageNamesBackFile() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var back = new MockMultipartFile("back", "aadhaar_rear.jpg", "image/jpeg", new byte[]{1});
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Name: Test Lawyer\nABCDE1234F")));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(), Map.of()));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, back);

        assertEquals("aadhaar_rear.jpg", response.getFile());
        assertEquals(com.askvocate.backend.model.VerificationStatus.FAILED, response.getVerificationStatus());
    }

    @Test
    void successfulAadhaarStoresFullNumberButOnlyUploadResponseRevealsIt() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setName("Test Lawyer");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        String number = validAadhaarNumber();
        var back = new MockMultipartFile("back", "aadhaar_back.jpg", "image/jpeg", new byte[]{1});
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Aadhaar\nName: Test Lawyer\nDOB: 01/02/1990\nMale\n" + number)));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "UIDAI\nAddress: 12 Test Road\nNew Delhi 110001")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            doc.setId("doc-id");
            stored.set(doc);
            return doc;
        });

        var response = service.verifyDocument("fresher-id", DocumentType.AADHAAR, front, back);

        assertEquals(number, stored.get().getAadhaarNumber());
        assertEquals(number, response.getAadhaarNumber());
        assertEquals("XXXX-XXXX-" + number.substring(8), response.getMaskedDocumentNumber());
        when(documents.findById("doc-id")).thenReturn(Optional.of(stored.get()));
        assertNull(service.getDocumentById("fresher-id", "doc-id").getAadhaarNumber());
    }

    private String validAadhaarNumber() {
        OcrExtractionService ocr = new OcrExtractionService();
        for (int digit = 0; digit < 10; digit++) {
            String candidate = "23456789012" + digit;
            if (ocr.isValidAadhaarChecksum(candidate)) return candidate;
        }
        throw new AssertionError("Unable to create checksum-valid test number");
    }

    @Test
    void existingExperiencedLawyerCanValidateBarNumber() {
        var profile = new LawyerExperiencedProfile();
        profile.setId("experienced-id");
        profile.setName("Test Lawyer");
        when(experienced.findById("experienced-id")).thenReturn(Optional.of(profile));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new BarCouncilVerificationRequest();
        request.setUserId("experienced-id");
        request.setBarCouncilNumber("D/1234/2021");

        var response = service.verifyBarCouncilDirect(request);

        assertEquals(com.askvocate.backend.model.VerificationStatus.VERIFIED, response.getVerificationStatus());
        verify(experienced).save(profile);
        verify(freshers, never()).save(any());
    }
}
