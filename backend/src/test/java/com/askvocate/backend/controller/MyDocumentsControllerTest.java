package com.askvocate.backend.controller;

import com.askvocate.backend.model.*;
import com.askvocate.backend.repository.*;
import com.askvocate.backend.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyDocumentsControllerTest {
    @Mock AppSessionService sessions;
    @Mock UserDocumentRepository verifiedDocs;
    @Mock UserDocRepository uploadedDocs;
    @Mock SelfieVerificationRepository selfies;
    @Mock DocumentVerificationService verifier;
    @Mock DocumentService uploads;
    @Mock CloudinaryService cloudinary;
    @InjectMocks MyDocumentsController controller;

    @Test void clientCannotAccessVerificationCatalogue() {
        when(sessions.requireAccount("Bearer client")).thenReturn(new AppSessionService.Account("c1", "CLIENT"));
        var error = assertThrows(ResponseStatusException.class, () -> controller.list("Bearer client"));
        assertTrue(error.getReason().contains("only available for lawyer accounts"));
        assertThrows(ResponseStatusException.class, () -> controller.detail("Bearer client", "AADHAAR"));
        verifyNoInteractions(verifiedDocs);
    }

    @Test void verifiedDocumentDetailsAreScopedToSessionAccount() {
        when(sessions.requireAccount("Bearer lawyer")).thenReturn(new AppSessionService.Account("owner", "LAWYER_FRESHER"));
        UserDocument doc = new UserDocument();
        doc.setDocumentType(DocumentType.PAN);
        doc.setVerificationStatus(VerificationStatus.VERIFIED);
        doc.setMaskedDocumentNumber("XXXXXX1234");
        doc.setCloudinaryReferences(List.of(new CloudinaryRef("private-id", "https://res.cloudinary.com/x", "front")));
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.PAN)).thenReturn(Optional.of(doc));
        var item = controller.detail("Bearer lawyer", "PAN").getBody();
        assertEquals("VERIFIED", item.status());
        assertEquals(List.of("front"), item.media());
        assertEquals("XXXXXX1234", item.maskedNumber());
        assertNull(item.note());
        verify(verifiedDocs).findByUserIdAndDocumentType("owner", DocumentType.PAN);
    }

    @Test void verifiedSelfieDoesNotExposeOldOrNullFailureText() {
        when(sessions.requireAccount("Bearer lawyer")).thenReturn(new AppSessionService.Account("owner", "LAWYER_FRESHER"));
        SelfieVerification selfie = new SelfieVerification();
        selfie.setVerificationStatus(VerificationStatus.VERIFIED);
        selfie.setFailureReason("null");
        when(selfies.findByUserIdOrderByCreatedAtDesc("owner")).thenReturn(List.of(selfie));

        var item = controller.detail("Bearer lawyer", "SELFIE").getBody();

        assertEquals("VERIFIED", item.status());
        assertNull(item.note());
    }

    @Test void fullAadhaarNumberIsOnlyInAuthenticatedDetail() {
        when(sessions.requireAccount("Bearer lawyer")).thenReturn(new AppSessionService.Account("owner", "LAWYER_FRESHER"));
        UserDocument aadhaar = new UserDocument();
        aadhaar.setDocumentType(DocumentType.AADHAAR);
        aadhaar.setVerificationStatus(VerificationStatus.VERIFIED);
        aadhaar.setMaskedDocumentNumber("XXXX-XXXX-0123");
        aadhaar.setAadhaarNumber("234567890123");
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.AADHAAR))
                .thenReturn(Optional.of(aadhaar));
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.PAN))
                .thenReturn(Optional.empty());

        var listed = controller.list("Bearer lawyer").getFirst();
        var detail = controller.detail("Bearer lawyer", "AADHAAR").getBody();

        assertEquals("XXXX-XXXX-0123", listed.maskedNumber());
        assertNull(listed.aadhaarNumber());
        assertEquals("234567890123", detail.aadhaarNumber());
    }

    @Test void verifiedPanDetailIncludesFullPanNumber() {
        when(sessions.requireAccount("Bearer lawyer")).thenReturn(new AppSessionService.Account("owner", "LAWYER_FRESHER"));
        UserDocument pan = new UserDocument();
        pan.setDocumentType(DocumentType.PAN);
        pan.setVerificationStatus(VerificationStatus.VERIFIED);
        pan.setMaskedDocumentNumber("XXXXXX234C");
        pan.setPanNumber("AAAPL1234C");
        when(verifiedDocs.findByUserIdAndDocumentType(anyString(), any())).thenReturn(Optional.empty());
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.PAN)).thenReturn(Optional.of(pan));

        var listed = controller.list("Bearer lawyer").stream()
                .filter(item -> item.type().equals("PAN")).findFirst().orElseThrow();
        var detail = controller.detail("Bearer lawyer", "PAN").getBody();
        var revealed = controller.revealNumber("Bearer lawyer", "PAN").getBody();

        assertNull(listed.panNumber());
        assertEquals("AAAPL1234C", detail.panNumber());
        assertEquals("AAAPL1234C", revealed.get("panNumber"));
    }

    @Test void verifiedPanDetailIncludesSignedQrPhoto() {
        when(sessions.requireAccount("Bearer lawyer")).thenReturn(new AppSessionService.Account("owner", "LAWYER_FRESHER"));
        UserDocument pan = new UserDocument();
        pan.setDocumentType(DocumentType.PAN);
        pan.setVerificationStatus(VerificationStatus.VERIFIED);
        pan.setPanQrPhotoUrl("https://res.cloudinary.com/demo/pan-qr.jpg");
        when(verifiedDocs.findByUserIdAndDocumentType(anyString(), any())).thenReturn(Optional.empty());
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.PAN)).thenReturn(Optional.of(pan));

        var item = controller.detail("Bearer lawyer", "PAN").getBody();

        assertEquals("https://res.cloudinary.com/demo/pan-qr.jpg", item.qrPhotoUrl());
    }

    @Test void verifiedAadhaarDetailUsesSignedQrData() {
        when(sessions.requireAccount("Bearer lawyer")).thenReturn(new AppSessionService.Account("owner", "LAWYER_FRESHER"));
        UserDocument doc = new UserDocument();
        doc.setDocumentType(DocumentType.AADHAAR);
        doc.setVerificationStatus(VerificationStatus.VERIFIED);
        doc.setMaskedDocumentNumber("XXXX-XXXX-1234");
        doc.setExtractedData(Map.of("name", "OCR N@me", "dob", "01/02/1990", "gender", "FEMALE"));
        QrData qr = new QrData();
        qr.setName("QR Name");
        qr.setDob("01-02-1990");
        qr.setGender("M");
        qr.setAddress("12 Road, Delhi");
        qr.setPincode("110001");
        qr.setAadhaarLast4("1234");
        qr.setPhotoUrl("https://res.cloudinary.com/demo/aadhaar-qr.jpg");
        doc.setQrData(qr);
        doc.setIsQrVerified(true);
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.AADHAAR)).thenReturn(Optional.of(doc));

        var item = controller.detail("Bearer lawyer", "AADHAAR").getBody();

        assertEquals("QR Name", item.qrDetails().get("Name"));
        assertEquals("01-02-1990", item.qrDetails().get("Date of Birth"));
        assertEquals("FEMALE", item.qrDetails().get("Gender"));
        assertEquals("OCR N@me", item.details().get("name"));
        assertNull(item.qrPhotoUrl());
    }
}
