package com.askvocate.backend.service;

import com.askvocate.backend.entity.*;
import com.askvocate.backend.model.User;
import com.askvocate.backend.model.UserDoc;
import com.askvocate.backend.model.VerificationQueueItem;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VerificationService {

    private final Firestore firestore;

    @Autowired
    public VerificationService(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Uploads document to Firebase Storage and writes metadata to Firestore sub-collection.
     */
    public UserDoc uploadDocument(String userId, DocType docType, byte[] fileBytes, String originalFileName) 
            throws InterruptedException, ExecutionException {
        
        String extension = getFileExtension(originalFileName);
        String storagePath = "documents/" + userId + "/" + docType.name() + "_" + System.currentTimeMillis() + "." + extension;
        String contentType = getContentType(extension);

        // 1. Upload file to Cloud Storage
        Bucket bucket = StorageClient.getInstance().bucket();
        bucket.create(storagePath, fileBytes, contentType);

        // 2. Generate a temporary signed URL (valid for 7 days)
        Blob blob = bucket.get(storagePath);
        String fileUrl = "";
        if (blob != null) {
            URL signedUrl = blob.signUrl(7, TimeUnit.DAYS);
            fileUrl = signedUrl.toString();
        }

        // 3. Save metadata to Firestore under /users/{userId}/documents/{docType}
        UserDoc userDoc = UserDoc.builder()
                .id(docType.name())
                .userId(userId)
                .docType(docType)
                .fileUrl(fileUrl)
                .storagePath(storagePath)
                .ocrProcessed(false)
                .extractedData(null)
                .tamperFlagged(false)
                .tamperConfidence(null)
                .status(DocStatus.UPLOADED)
                .uploadedAt(System.currentTimeMillis())
                .build();

        firestore.collection("users")
                .document(userId)
                .collection("documents")
                .document(docType.name())
                .set(userDoc)
                .get(); // block until completion

        return userDoc;
    }

    /**
     * Validates required documents based on user type and updates status to UNDER_VERIFICATION.
     * Pushes item to the admin verification queue.
     */
    public void submitForVerification(String userId) throws InterruptedException, ExecutionException {
        // 1. Retrieve User
        DocumentSnapshot userSnap = firestore.collection("users").document(userId).get().get();
        if (!userSnap.exists()) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }
        User user = userSnap.toObject(User.class);

        // 2. Fetch all uploaded documents
        List<QueryDocumentSnapshot> docSnaps = firestore.collection("users")
                .document(userId)
                .collection("documents")
                .get()
                .get()
                .getDocuments();

        Set<DocType> uploadedTypes = docSnaps.stream()
                .map(d -> d.toObject(UserDoc.class).getDocType())
                .collect(Collectors.toSet());

        // 3. Determine and check required document types
        List<DocType> requiredTypes = getRequiredDocsForRole(user);
        List<DocType> missingTypes = new ArrayList<>();

        // Aadhaar OR PAN is required as the primary identity document
        boolean hasIdentityDoc = uploadedTypes.contains(DocType.AADHAAR) || uploadedTypes.contains(DocType.PAN);
        if (!hasIdentityDoc) {
            missingTypes.add(DocType.AADHAAR); // Flag Aadhaar as missing by default if neither is present
        }

        for (DocType required : requiredTypes) {
            // Identity doc requirement is handled above separately (Aadhaar or PAN)
            if (required == DocType.AADHAAR || required == DocType.PAN) {
                continue;
            }
            if (!uploadedTypes.contains(required)) {
                missingTypes.add(required);
            }
        }

        if (!missingTypes.isEmpty()) {
            throw new IllegalStateException("Cannot submit verification. Missing required documents: " + missingTypes);
        }

        // 4. Update user status in Firestore
        user.setVerificationStatus(Verification_Status.UNDER_VERIFICATION);
        firestore.collection("users").document(userId).set(user).get();

        // 5. Create or update admin verification queue item
        int flaggedDocCount = 0;
        for (QueryDocumentSnapshot docSnap : docSnaps) {
            UserDoc doc = docSnap.toObject(UserDoc.class);
            if (doc.isTamperFlagged()) {
                flaggedDocCount++;
            }
        }

        VerificationQueueItem queueItem = VerificationQueueItem.builder()
                .userId(userId)
                .userName(user.getName())
                .role(user.getRole())
                .submittedAt(System.currentTimeMillis())
                .status("UNDER_VERIFICATION")
                .flaggedDocCount(flaggedDocCount)
                .build();

        firestore.collection("verificationQueue").document(userId).set(queueItem).get();
    }

    /**
     * Resolves pending verification request. Updates user status and clears/removes queue item.
     */
    public void processVerification(String userId, Verification_Status newStatus, String rejectionReason) 
            throws InterruptedException, ExecutionException {
        
        // 1. Fetch User
        DocumentSnapshot userSnap = firestore.collection("users").document(userId).get().get();
        if (!userSnap.exists()) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }
        User user = userSnap.toObject(User.class);

        // 2. Set new status and timestamps
        user.setVerificationStatus(newStatus);
        if (newStatus == Verification_Status.VERIFIED) {
            user.setVerifiedAt(System.currentTimeMillis());
            user.setRejectionReason(null);
        } else if (newStatus == Verification_Status.REJECTED) {
            user.setVerifiedAt(null);
            user.setRejectionReason(rejectionReason);
        }

        firestore.collection("users").document(userId).set(user).get();

        // 3. Remove resolved user from admin verification queue
        firestore.collection("verificationQueue").document(userId).delete().get();
    }

    /**
     * Generates a temporary signed URL for a specific document to view it securely.
     */
    public String getDocumentSignedUrl(String userId, DocType docType) 
            throws InterruptedException, ExecutionException {
        
        DocumentSnapshot docSnap = firestore.collection("users")
                .document(userId)
                .collection("documents")
                .document(docType.name())
                .get()
                .get();

        if (!docSnap.exists()) {
            throw new IllegalArgumentException("Document not found for type: " + docType);
        }
        UserDoc userDoc = docSnap.toObject(UserDoc.class);

        Bucket bucket = StorageClient.getInstance().bucket();
        Blob blob = bucket.get(userDoc.getStoragePath());
        if (blob == null) {
            throw new IllegalStateException("Physical file not found in storage path: " + userDoc.getStoragePath());
        }

        return blob.signUrl(15, TimeUnit.MINUTES).toString();
    }

    private List<DocType> getRequiredDocsForRole(User user) {
        List<DocType> required = new ArrayList<>();
        
        // Everyone needs identity document (Aadhaar or PAN) and Selfie
        required.add(DocType.AADHAAR); // Handled as Aadhaar OR PAN
        required.add(DocType.SELFIE);

        if (user.getRole() == Role.LAWYER_FRESHER || user.getRole() == Role.LAWYER_EXPERIENCED) {
            // Both freshers and experienced lawyers need Degree and Bar registration
            required.add(DocType.DEGREE_CERTIFICATE);
            required.add(DocType.BAR_CERTIFICATE);

            if (user.getRole() == Role.LAWYER_EXPERIENCED) {
                // Experienced lawyers also need COP and AIBE clearing proof
                required.add(DocType.COP);
                required.add(DocType.AIBE_CERTIFICATE);
            }
        }

        return required;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "jpg";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private String getContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
