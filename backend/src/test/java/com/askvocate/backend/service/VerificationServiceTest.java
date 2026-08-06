package com.askvocate.backend.service;

import com.askvocate.backend.entity.*;
import com.askvocate.backend.model.User;
import com.askvocate.backend.model.UserDoc;
import com.askvocate.backend.model.VerificationQueueItem;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VerificationServiceTest {

    @Mock
    private Firestore firestore;

    @Mock
    private CollectionReference usersCollection;

    @Mock
    private DocumentReference userDocRef;

    @Mock
    private ApiFuture<DocumentSnapshot> userFuture;

    @Mock
    private DocumentSnapshot userSnapshot;

    @Mock
    private CollectionReference documentsSubCollection;

    @Mock
    private ApiFuture<QuerySnapshot> documentsFuture;

    @Mock
    private QuerySnapshot querySnapshot;

    @Mock
    private CollectionReference verificationQueueCollection;

    @Mock
    private DocumentReference queueItemDocRef;

    @Mock
    private ApiFuture<WriteResult> writeResultFuture;

    @InjectMocks
    private VerificationService verificationService;

    private final String testUserId = "usr_test123";

    @Test
    public void testSubmitForVerification_Success_FresherLawyer() throws Exception {
        // Setup User
        User user = User.builder()
                .id(testUserId)
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .role(Role.LAWYER_FRESHER)
                .verificationStatus(Verification_Status.PENDING)
                .build();

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(testUserId)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.toObject(User.class)).thenReturn(user);

        // Mock Fetch Documents: Fresher needs Aadhaar/PAN, Selfie, Degree, Bar
        QueryDocumentSnapshot docAadhaar = mock(QueryDocumentSnapshot.class);
        UserDoc aadhaarModel = UserDoc.builder().docType(DocType.AADHAAR).isTamperFlagged(false).build();
        when(docAadhaar.toObject(UserDoc.class)).thenReturn(aadhaarModel);

        QueryDocumentSnapshot docSelfie = mock(QueryDocumentSnapshot.class);
        UserDoc selfieModel = UserDoc.builder().docType(DocType.SELFIE).isTamperFlagged(false).build();
        when(docSelfie.toObject(UserDoc.class)).thenReturn(selfieModel);

        QueryDocumentSnapshot docDegree = mock(QueryDocumentSnapshot.class);
        UserDoc degreeModel = UserDoc.builder().docType(DocType.DEGREE_CERTIFICATE).isTamperFlagged(false).build();
        when(docDegree.toObject(UserDoc.class)).thenReturn(degreeModel);

        QueryDocumentSnapshot docBar = mock(QueryDocumentSnapshot.class);
        UserDoc barModel = UserDoc.builder().docType(DocType.BAR_CERTIFICATE).isTamperFlagged(true).build(); // 1 flagged doc
        when(docBar.toObject(UserDoc.class)).thenReturn(barModel);

        List<QueryDocumentSnapshot> mockDocsList = List.of(docAadhaar, docSelfie, docDegree, docBar);

        when(userDocRef.collection("documents")).thenReturn(documentsSubCollection);
        when(documentsSubCollection.get()).thenReturn(documentsFuture);
        when(documentsFuture.get()).thenReturn(querySnapshot);
        when(querySnapshot.getDocuments()).thenReturn(mockDocsList);

        // Mock saving updated user status
        when(userDocRef.set(any(User.class))).thenReturn(writeResultFuture);

        // Mock verificationQueue save
        when(firestore.collection("verificationQueue")).thenReturn(verificationQueueCollection);
        when(verificationQueueCollection.document(testUserId)).thenReturn(queueItemDocRef);
        when(queueItemDocRef.set(any(VerificationQueueItem.class))).thenReturn(writeResultFuture);

        // Execute
        assertDoesNotThrow(() -> verificationService.submitForVerification(testUserId));

        // Verify status was updated to UNDER_VERIFICATION
        assertEquals(Verification_Status.UNDER_VERIFICATION, user.getVerificationStatus());
        
        // Verify queue item was created and flagged count was calculated correctly
        verify(queueItemDocRef).set(argThat(item -> 
            item.getUserId().equals(testUserId) && 
            item.getFlaggedDocCount() == 1 &&
            item.getRole() == Role.LAWYER_FRESHER
        ));
    }

    @Test
    public void testSubmitForVerification_ThrowsException_MissingRequiredDocs() throws Exception {
        // Setup Experienced Lawyer who is missing COP and AIBE
        User user = User.builder()
                .id(testUserId)
                .name("Experienced John")
                .role(Role.LAWYER_EXPERIENCED)
                .verificationStatus(Verification_Status.PENDING)
                .build();

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(testUserId)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.toObject(User.class)).thenReturn(user);

        // Provide only Aadhaar and Selfie (Missing Degree, Bar, COP, AIBE)
        QueryDocumentSnapshot docAadhaar = mock(QueryDocumentSnapshot.class);
        UserDoc aadhaarModel = UserDoc.builder().docType(DocType.AADHAAR).build();
        when(docAadhaar.toObject(UserDoc.class)).thenReturn(aadhaarModel);

        QueryDocumentSnapshot docSelfie = mock(QueryDocumentSnapshot.class);
        UserDoc selfieModel = UserDoc.builder().docType(DocType.SELFIE).build();
        when(docSelfie.toObject(UserDoc.class)).thenReturn(selfieModel);

        List<QueryDocumentSnapshot> mockDocsList = List.of(docAadhaar, docSelfie);

        when(userDocRef.collection("documents")).thenReturn(documentsSubCollection);
        when(documentsSubCollection.get()).thenReturn(documentsFuture);
        when(documentsFuture.get()).thenReturn(querySnapshot);
        when(querySnapshot.getDocuments()).thenReturn(mockDocsList);

        // Execute and Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            verificationService.submitForVerification(testUserId);
        });

        assertTrue(exception.getMessage().contains("DEGREE_CERTIFICATE"));
        assertTrue(exception.getMessage().contains("BAR_CERTIFICATE"));
        assertTrue(exception.getMessage().contains("COP"));
        assertTrue(exception.getMessage().contains("AIBE_CERTIFICATE"));
    }

    @Test
    public void testProcessVerification_Approve() throws Exception {
        User user = User.builder()
                .id(testUserId)
                .verificationStatus(Verification_Status.UNDER_VERIFICATION)
                .build();

        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(testUserId)).thenReturn(userDocRef);
        when(userDocRef.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.toObject(User.class)).thenReturn(user);
        when(userDocRef.set(user)).thenReturn(writeResultFuture);

        when(firestore.collection("verificationQueue")).thenReturn(verificationQueueCollection);
        when(verificationQueueCollection.document(testUserId)).thenReturn(queueItemDocRef);
        when(queueItemDocRef.delete()).thenReturn(writeResultFuture);

        verificationService.processVerification(testUserId, Verification_Status.VERIFIED, null);

        assertEquals(Verification_Status.VERIFIED, user.getVerificationStatus());
        assertNotNull(user.getVerifiedAt());
        assertNull(user.getRejectionReason());

        verify(queueItemDocRef).delete();
    }
}
