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

    @Test void clientCatalogueContainsOnlyIdentityDocuments() {
        when(sessions.requireAccount("Bearer client")).thenReturn(new AppSessionService.Account("c1", "CLIENT"));
        when(verifiedDocs.findByUserIdAndDocumentType(anyString(), any())).thenReturn(Optional.empty());
        var items = controller.list("Bearer client");
        assertEquals(List.of("AADHAAR", "PAN"), items.stream().map(MyDocumentsController.Item::type).toList());
        assertThrows(ResponseStatusException.class, () -> controller.detail("Bearer client", "BAR_COUNCIL_ID"));
        verify(verifiedDocs, never()).findByUserIdAndDocumentType("c1", DocumentType.BAR_COUNCIL_ID);
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
        when(sessions.requireAccount("Bearer client")).thenReturn(new AppSessionService.Account("owner", "CLIENT"));
        UserDocument aadhaar = new UserDocument();
        aadhaar.setDocumentType(DocumentType.AADHAAR);
        aadhaar.setVerificationStatus(VerificationStatus.VERIFIED);
        aadhaar.setMaskedDocumentNumber("XXXX-XXXX-0123");
        aadhaar.setAadhaarNumber("234567890123");
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.AADHAAR))
                .thenReturn(Optional.of(aadhaar));
        when(verifiedDocs.findByUserIdAndDocumentType("owner", DocumentType.PAN))
                .thenReturn(Optional.empty());

        var listed = controller.list("Bearer client").getFirst();
        var detail = controller.detail("Bearer client", "AADHAAR").getBody();

        assertEquals("XXXX-XXXX-0123", listed.maskedNumber());
        assertNull(listed.aadhaarNumber());
        assertEquals("234567890123", detail.aadhaarNumber());
    }
}
