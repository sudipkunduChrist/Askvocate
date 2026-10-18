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
import com.askvocate.backend.repository.SelfieVerificationRepository;
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
    private final VisionVerificationClient vision = mock(VisionVerificationClient.class);
    private final SelfieVerificationRepository selfies = mock(SelfieVerificationRepository.class);
    private final DocumentVerificationService service = new DocumentVerificationService(cloudinary,
            new OcrExtractionService(), new BarCouncilValidationService(), documents, experienced, freshers, clients,
            vision, selfies, "test-hmac-key");
    private final MockMultipartFile front = new MockMultipartFile("front", "photo.jpg", "image/jpeg", new byte[]{1});

    @BeforeEach
    void resetMocks() {
        reset(cloudinary, documents, experienced, freshers, clients, vision, selfies);
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
    void clientCannotVerifyAnyDocumentTypeOrBarNumber() {
        when(clients.existsById("client-id")).thenReturn(true);
        for (DocumentType type : List.of(DocumentType.PAN, DocumentType.AADHAAR,
                DocumentType.BAR_COUNCIL_ID, DocumentType.BAR_CERTIFICATE,
                DocumentType.CERTIFICATE_OF_PRACTICE)) {
            var error = assertThrows(DocumentVerificationException.class,
                    () -> service.verifyDocument("client-id", type, front, front));
            assertTrue(error.getMessage().contains("only available for LAWYER_FRESHER and LAWYER_EXPERIENCED"));
        }
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
        when(selfies.existsByUserIdAndAadhaarDocumentIdAndAadhaarSubmissionTokenAndVerificationStatus(
                eq("experienced-id"), eq("test-aadhaar"), eq("aadhaar-revision"),
                eq(com.askvocate.backend.model.VerificationStatus.VERIFIED))).thenReturn(true);

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
        document.setId(type == DocumentType.AADHAAR ? "test-aadhaar" : type.name());
        document.setVerificationStatus(com.askvocate.backend.model.VerificationStatus.VERIFIED);
        if (type == DocumentType.AADHAAR) {
            document.setIsQrVerified(true);
            document.setQrPrintedMismatch(false);
            document.setSubmissionToken("aadhaar-revision");
        }
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
    void unavailableBackendOcrNamesTheCauseAndFileWithoutSavingFailedVerification() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenThrow(new DocumentVerificationException("Backend OCR is unavailable. Check Python and Tesseract."));

        var error = assertThrows(DocumentVerificationException.class,
                () -> service.verifyDocument("fresher-id", DocumentType.PAN, front, null));

        assertTrue(error.getMessage().contains("Backend OCR is unavailable"));
        assertEquals("photo.jpg", error.getFile());
        var response = new GlobalExceptionHandler().handleDocumentVerification(error);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("photo.jpg", response.getBody().get("file"));
        verify(documents, never()).save(any());
    }

    @Test
    void failedBackImageNamesBackFile() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var back = new MockMultipartFile("back", "aadhaar_rear.jpg", "image/jpeg", new byte[]{1});
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Aadhaar\nName: Test Lawyer\nDOB: 01/02/1990\nMale\n" + validAadhaarNumber())));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(), Map.of()));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.verifyDocument("fresher-id", DocumentType.AADHAAR, front, back);

        assertEquals("aadhaar_rear.jpg", response.getFile());
        assertEquals(com.askvocate.backend.model.VerificationStatus.FAILED, response.getVerificationStatus());
    }

    @Test
    void aadhaarStoresQrDataAlongsideOcrFields() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setName("Test Lawyer");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        String number = validAadhaarNumber();
        var back = new MockMultipartFile("back", "aadhaar_back.jpg", "image/jpeg", new byte[]{1});
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Name: Test Lawyer\nDOB: 01/02/1990\nMale\n" + number)));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Address: 12 Test Road\nNew Delhi")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            doc.setId("doc-id");
            stored.set(doc);
            return doc;
        });
        when(vision.verifyAadhaarQr(any(), anyMap(), anyString())).thenReturn(
                new VisionVerificationClient.QrResult(true, Map.of("name", "Test Lawyer",
                        "dob", "01/02/1990", "gender", "M", "pincode", "201304",
                        "address", "12 Test Road New Delhi", "aadhaarLast4", number.substring(8)),
                        true, "uidai-test.cer", "4626", List.of(), false, null, "V2", "AQID"));
        when(cloudinary.uploadBytes(any(), anyString(), eq("qr"))).thenReturn(
                new com.askvocate.backend.model.CloudinaryRef("qr-photo-id",
                        "https://res.cloudinary.com/test/qr.jpg", "qr"));

        var response = service.verifyDocument("fresher-id", DocumentType.AADHAAR, front, back);

        assertEquals(number, stored.get().getAadhaarNumber());
        assertNotNull(stored.get().getAadhaarNumberHash());
        assertEquals(com.askvocate.backend.model.VerificationStatus.VERIFIED, response.getVerificationStatus());
        assertEquals(number, response.getAadhaarNumber());
        assertEquals("Test Lawyer", response.getExtractedFields().get("name"));
        assertEquals("01/02/1990", response.getExtractedFields().get("dob"));
        assertEquals("MALE", response.getExtractedFields().get("gender"));
        assertEquals("12 Test Road New Delhi", response.getExtractedFields().get("address"));
        assertEquals("Name: Test Lawyer\nDOB: 01/02/1990\nMale\n" + number,
                stored.get().getOcrText().get("front"));
        assertEquals("Address: 12 Test Road\nNew Delhi", stored.get().getOcrText().get("back"));
        assertNull(response.getFile());
        assertEquals(Boolean.TRUE, response.getIsQrVerified());
        assertNotNull(stored.get().getQrData());
        assertEquals("Test Lawyer", stored.get().getQrData().getName());
        assertEquals(number.substring(8), stored.get().getQrData().getAadhaarLast4());
        assertEquals("4626", stored.get().getQrRawText());
        assertNotNull(stored.get().getQrText());
        assertTrue(stored.get().getQrText().contains("\"name\":\"Test Lawyer\""));
        assertEquals("V2", stored.get().getQrData().getVersion());
        assertEquals("qr-photo-id", stored.get().getQrData().getPhotoPublicId());
        assertEquals("https://res.cloudinary.com/test/qr.jpg", stored.get().getQrData().getPhotoUrl());
        assertEquals("back", stored.get().getQrImageSide());
        assertFalse(stored.get().getQrPrintedMismatch());
        assertTrue(stored.get().getAutoApproved());
        assertFalse(stored.get().getRequiresManualReview());
        verify(vision, atLeastOnce()).verifyAadhaarQr(any(), anyMap(), anyString());
        assertEquals("XXXX-XXXX-" + number.substring(8), response.getMaskedDocumentNumber());
        when(documents.findById("doc-id")).thenReturn(Optional.of(stored.get()));
        assertNull(service.getDocumentById("fresher-id", "doc-id").getAadhaarNumber());
    }

    @Test
    void aadhaarQrMismatchRequiresManualReview() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setName("Test Lawyer");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        String number = validAadhaarNumber();
        var back = new MockMultipartFile("back", "aadhaar_back.jpg", "image/jpeg", new byte[]{1});
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Name: Test Lawyer\nDOB: 01/02/1990\nMale\n" + number)));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Address: 12 Test Road\nNew Delhi")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            doc.setId("doc-id");
            stored.set(doc);
            return doc;
        });
        when(vision.verifyAadhaarQr(any(), anyMap(), anyString())).thenReturn(
                new VisionVerificationClient.QrResult(true, Map.of("name", "Tampered Name",
                        "dob", "01/02/1990", "gender", "M", "pincode", "201304",
                        "address", "12 Test Road New Delhi", "aadhaarLast4", number.substring(8)),
                        true, "uidai-test.cer", "4626", List.of("name"), true, null, "V2", null));

        var response = service.verifyDocument("fresher-id", DocumentType.AADHAAR, front, back);

        assertEquals(com.askvocate.backend.model.VerificationStatus.PENDING, response.getVerificationStatus());
        assertEquals(Boolean.TRUE, stored.get().getIsQrVerified());
        assertEquals(Boolean.TRUE, stored.get().getQrPrintedMismatch());
        assertEquals(List.of("name"), stored.get().getQrMismatchFields());
        assertTrue(stored.get().getRequiresManualReview());
        assertFalse(stored.get().getAutoApproved());
        assertTrue(stored.get().getManualReviewReason().contains("name"));
        assertNull(stored.get().getVerifiedAt());
        assertNotNull(stored.get().getQrText());
        assertEquals("Tampered Name", stored.get().getQrData().getName());
    }

    @Test
    void aadhaarWithoutSignedQrFailsVerification() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        String number = validAadhaarNumber();
        var back = new MockMultipartFile("back", "aadhaar_back.jpg", "image/jpeg", new byte[]{1});
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Name: Test Lawyer\nDOB: 01/02/1990\nMale\n" + number)));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Address: 12 Test Road\nNew Delhi")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(vision.verifyAadhaarQr(any(), anyMap(), anyString())).thenReturn(
                VisionVerificationClient.QrResult.unavailable("No readable Aadhaar Secure QR code was found."));

        var response = service.verifyDocument("fresher-id", DocumentType.AADHAAR, front, back);

        assertEquals(com.askvocate.backend.model.VerificationStatus.FAILED, response.getVerificationStatus());
        assertTrue(response.getFailureReason().contains("Secure QR"));
        assertEquals("aadhaar_back.jpg", response.getFile());
        assertFalse(stored.get().getIsQrVerified());
        assertNull(stored.get().getQrData());
        assertNull(stored.get().getQrText());
        assertTrue(stored.get().getCloudinaryReferences().isEmpty());
    }

    @Test
    void failedAadhaarStillStoresRawOcrAndFieldsAlreadyRead() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var back = new MockMultipartFile("back", "aadhaar_back.jpg", "image/jpeg", new byte[]{1});
        String frontText = "Name: Test Lawyer\nIssue Date: 01/02/2020\nMale\n" + validAadhaarNumber();
        String backText = "Address: 12 Test Road\nNew Delhi";
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", frontText)));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", backText)));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return stored.get();
        });

        var response = service.verifyDocument("fresher-id", DocumentType.AADHAAR, front, back);

        assertEquals(com.askvocate.backend.model.VerificationStatus.FAILED, response.getVerificationStatus());
        assertEquals("photo.jpg", response.getFile());
        assertEquals("Test Lawyer", stored.get().getExtractedData().get("name"));
        assertEquals("12 Test Road New Delhi", stored.get().getExtractedData().get("address"));
        assertFalse(stored.get().getExtractedData().containsKey("dob"));
        assertEquals(frontText, stored.get().getOcrText().get("front"));
        assertEquals(backText, stored.get().getOcrText().get("back"));
        assertTrue(response.getFailureReason().contains("date of birth"));
        verify(vision, atLeastOnce()).verifyAadhaarQr(any(), anyMap(), anyString());
        assertNull(stored.get().getQrData());
        assertNull(stored.get().getQrRawText());
        assertNull(stored.get().getQrText());
        assertFalse(stored.get().getIsQrVerified());
    }

    @Test
    void panWithoutVerifiedAadhaarWaitsForManualReview() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setName("Test Lawyer");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                                + "Father's Name: Test Father\nDOB: 01/02/1990")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            doc.setId("pan-id");
            stored.set(doc);
            return doc;
        });

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);

        assertEquals(com.askvocate.backend.model.VerificationStatus.PENDING, response.getVerificationStatus());
        assertEquals("AAAPL1234C", stored.get().getPanNumber());
        assertNotNull(stored.get().getPanNumberHash());
        assertTrue(stored.get().getRequiresManualReview());
        assertTrue(stored.get().getManualReviewReason().contains("Verified Aadhaar QR"));
        assertNull(stored.get().getCrossDocumentMismatchFields());
        assertNull(response.getPanNumber());
        assertEquals("XXXXXX234C", response.getMaskedDocumentNumber());
    }

    @Test
    void panMatchingSignedAadhaarQrIsVerified() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setName("Test Lawyer");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        when(documents.findByUserIdAndDocumentType("fresher-id", DocumentType.AADHAAR))
                .thenReturn(Optional.of(verifiedAadhaarWithQr("Test Lawyer", "01-02-1990")));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                                + "Father's Name: Test Father\nDOB: 01/02/1990")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            doc.setId("pan-id");
            stored.set(doc);
            return doc;
        });

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);

        assertEquals(com.askvocate.backend.model.VerificationStatus.VERIFIED, response.getVerificationStatus());
        assertEquals("AAAPL1234C", response.getPanNumber());
        assertTrue(stored.get().getAutoApproved());
        assertFalse(stored.get().getRequiresManualReview());
        assertNull(stored.get().getCrossDocumentMismatchFields());
    }

    @Test
    void panMismatchingSignedAadhaarQrRequiresManualReview() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setName("Test Lawyer");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        when(documents.findByUserIdAndDocumentType("fresher-id", DocumentType.AADHAAR))
                .thenReturn(Optional.of(verifiedAadhaarWithQr("Different Lawyer", "02-02-1990")));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                                + "Father's Name: Test Father\nDOB: 01/02/1990")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            doc.setId("pan-id");
            stored.set(doc);
            return doc;
        });

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);

        assertEquals(com.askvocate.backend.model.VerificationStatus.PENDING, response.getVerificationStatus());
        assertTrue(stored.get().getRequiresManualReview());
        assertEquals(List.of("name", "dob"), stored.get().getCrossDocumentMismatchFields());
        assertTrue(stored.get().getManualReviewReason().contains("do not match"));
        assertNull(response.getPanNumber());
    }

    @Test
    void duplicatePanRequiresManualReview() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var other = new com.askvocate.backend.model.UserDocument();
        other.setUserId("other-user");
        when(documents.findByPanNumberHash(anyString())).thenReturn(Optional.of(other));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                                + "Father's Name: Test Father\nDOB: 01/02/1990")));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            doc.setId("pan-id");
            stored.set(doc);
            return doc;
        });

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);

        assertEquals(com.askvocate.backend.model.VerificationStatus.PENDING, response.getVerificationStatus());
        assertTrue(stored.get().getRequiresManualReview());
        assertTrue(stored.get().getManualReviewReason().contains("another account"));
    }

    @Test
    void panIsAutoVerifiedWhenAadhaarQrIsVerifiedLater() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        profile.setName("Test Lawyer");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        String number = validAadhaarNumber();
        var aadhaarFront = new MockMultipartFile("front", "aadhaar_front.jpg", "image/jpeg", new byte[]{2});
        var back = new MockMultipartFile("back", "aadhaar_back.jpg", "image/jpeg", new byte[]{1});

        AtomicReference<com.askvocate.backend.model.UserDocument> panStored = new AtomicReference<>();
        AtomicReference<com.askvocate.backend.model.UserDocument> aadhaarStored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            com.askvocate.backend.model.UserDocument doc = invocation.getArgument(0);
            if (doc.getDocumentType() == DocumentType.PAN) {
                doc.setId("pan-id");
                panStored.set(doc);
            } else {
                doc.setId("aadhaar-id");
                aadhaarStored.set(doc);
            }
            return doc;
        });
        when(documents.findByUserIdAndDocumentType("fresher-id", DocumentType.PAN))
                .thenAnswer(invocation -> Optional.ofNullable(panStored.get()));
        when(documents.findByUserIdAndDocumentType("fresher-id", DocumentType.AADHAAR))
                .thenAnswer(invocation -> Optional.ofNullable(aadhaarStored.get()));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                                + "Father's Name: Test Father\nDOB: 01/02/1990")));
        when(cloudinary.uploadWithOcr(eq(aadhaarFront), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Name: Test Lawyer\nDOB: 01/02/1990\nMale\n" + number)));
        when(cloudinary.uploadWithOcr(eq(back), anyString(), eq("back")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "Address: 12 Test Road\nNew Delhi")));
        when(vision.verifyAadhaarQr(any(), anyMap(), anyString())).thenReturn(
                new VisionVerificationClient.QrResult(true, Map.of("name", "Test Lawyer",
                        "dob", "01-02-1990", "gender", "M", "pincode", "201304",
                        "address", "12 Test Road New Delhi", "aadhaarLast4", number.substring(8)),
                        true, "uidai-test.cer", "4626", List.of(), false, null, "V2", null));

        var panResponse = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);
        assertEquals(com.askvocate.backend.model.VerificationStatus.PENDING, panResponse.getVerificationStatus());

        var aadhaarResponse = service.verifyDocument("fresher-id", DocumentType.AADHAAR, aadhaarFront, back);

        assertEquals(com.askvocate.backend.model.VerificationStatus.VERIFIED,
                aadhaarResponse.getVerificationStatus());
        assertEquals(com.askvocate.backend.model.VerificationStatus.VERIFIED,
                panStored.get().getVerificationStatus());
        assertTrue(panStored.get().getAutoApproved());
        assertNotNull(panStored.get().getVerifiedAt());
    }

    @Test
    void failedPanKeepsRawOcrTextForDiagnosis() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        String ocrText = "Name: Test Lawyer\nFather's Name: Test Father\nDOB: 01/02/1990";
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", ocrText)));
        AtomicReference<com.askvocate.backend.model.UserDocument> stored = new AtomicReference<>();
        when(documents.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return stored.get();
        });

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);

        assertEquals(com.askvocate.backend.model.VerificationStatus.FAILED, response.getVerificationStatus());
        assertEquals("photo.jpg", response.getFile());
        assertEquals(ocrText, stored.get().getOcrText().get("front"));
        assertTrue(response.getFailureReason().contains("PAN number"));
    }

    @Test
    void failedPanReturnsFieldsOcrDidReadWithoutClaimingVerification() throws Exception {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        when(cloudinary.uploadWithOcr(eq(front), anyString(), eq("front")))
                .thenReturn(new CloudinaryService.UploadResult(new com.askvocate.backend.model.CloudinaryRef(),
                        Map.of("text", "AAAPL1234C\nFather's Name: Test Father\nDOB: 01/02/1990")));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.verifyDocument("fresher-id", DocumentType.PAN, front, null);

        assertEquals(com.askvocate.backend.model.VerificationStatus.FAILED, response.getVerificationStatus());
        assertEquals("Test Father", response.getExtractedFields().get("fatherName"));
        assertEquals("01/02/1990", response.getExtractedFields().get("dob"));
        assertFalse(response.getExtractedFields().containsKey("name"));
        assertNull(response.getPanNumber());
    }

    @Test
    void panBackImageIsRejectedBeforeUpload() {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var back = new MockMultipartFile("back", "pan_back.jpg", "image/jpeg", new byte[]{1});

        var error = assertThrows(DocumentVerificationException.class,
                () -> service.verifyDocument("fresher-id", DocumentType.PAN, front, back));

        assertEquals("pan_back.jpg", error.getFile());
        verifyNoInteractions(cloudinary, documents);
    }

    @Test
    void adminCanApprovePendingPan() {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var pan = new com.askvocate.backend.model.UserDocument();
        pan.setId("pan-id");
        pan.setUserId("fresher-id");
        pan.setDocumentType(DocumentType.PAN);
        pan.setVerificationStatus(com.askvocate.backend.model.VerificationStatus.PENDING);
        pan.setRequiresManualReview(true);
        pan.setManualReviewReason("Waiting for review.");
        when(documents.findById("pan-id")).thenReturn(Optional.of(pan));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.approveDocument("pan-id", "admin", "Checked against portal.");

        assertEquals(com.askvocate.backend.model.VerificationStatus.VERIFIED, saved.getVerificationStatus());
        assertEquals("APPROVED", saved.getReviewDecision());
        assertEquals("admin", saved.getReviewedBy());
        assertNotNull(saved.getVerifiedAt());
        assertFalse(saved.getRequiresManualReview());
    }

    @Test
    void adminCanRejectPendingPan() {
        var profile = new LawyerFresherProfile();
        profile.setId("fresher-id");
        when(freshers.findById("fresher-id")).thenReturn(Optional.of(profile));
        var pan = new com.askvocate.backend.model.UserDocument();
        pan.setId("pan-id");
        pan.setUserId("fresher-id");
        pan.setDocumentType(DocumentType.PAN);
        pan.setVerificationStatus(com.askvocate.backend.model.VerificationStatus.PENDING);
        pan.setRequiresManualReview(true);
        when(documents.findById("pan-id")).thenReturn(Optional.of(pan));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.rejectDocument("pan-id", "admin", "PAN image is blurry.");

        assertEquals(com.askvocate.backend.model.VerificationStatus.REJECTED, saved.getVerificationStatus());
        assertEquals("REJECTED", saved.getReviewDecision());
        assertTrue(saved.getFailureReason().contains("blurry"));
        assertNull(saved.getVerifiedAt());
    }

    @Test
    void panQueueCannotApproveAadhaarDocuments() {
        var aadhaar = new com.askvocate.backend.model.UserDocument();
        aadhaar.setId("aadhaar-id");
        aadhaar.setDocumentType(DocumentType.AADHAAR);
        aadhaar.setRequiresManualReview(true);
        when(documents.findById("aadhaar-id")).thenReturn(Optional.of(aadhaar));

        assertThrows(DocumentVerificationException.class,
                () -> service.approveDocument("aadhaar-id", "admin", null));
    }

    private com.askvocate.backend.model.UserDocument verifiedAadhaarWithQr(String name, String dob) {
        var aadhaar = new com.askvocate.backend.model.UserDocument();
        aadhaar.setUserId("fresher-id");
        aadhaar.setDocumentType(DocumentType.AADHAAR);
        aadhaar.setVerificationStatus(com.askvocate.backend.model.VerificationStatus.VERIFIED);
        aadhaar.setIsQrVerified(true);
        aadhaar.setQrPrintedMismatch(false);
        var qr = new com.askvocate.backend.model.QrData();
        qr.setName(name);
        qr.setDob(dob);
        aadhaar.setQrData(qr);
        return aadhaar;
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
