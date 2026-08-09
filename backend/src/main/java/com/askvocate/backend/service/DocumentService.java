package com.askvocate.backend.service;

import com.askvocate.backend.entity.DocType;
import com.askvocate.backend.model.UserDoc;
import com.askvocate.backend.repository.UserDocRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    @Autowired
    private UserDocRepository userDocRepository;

    @Autowired
    private CloudinaryService cloudinaryService;

    // ✅ Upload document - Saves to Cloudinary AND MongoDB
    public UserDoc uploadDocument(String userId, DocType docType, MultipartFile file) throws Exception {
        // 1. Upload to Cloudinary
        String folder = "documents/" + userId + "/" + docType.name().toLowerCase();
        String fileUrl = cloudinaryService.uploadFile(file, folder);

        // 2. Save metadata to MongoDB
        UserDoc userDoc = UserDoc.builder()
                .userId(userId)
                .docType(docType)
                .fileUrl(fileUrl)           // Cloudinary URL stored in MongoDB
                .storagePath(folder)        // Folder path stored in MongoDB
                .uploadedAt(Instant.now().toEpochMilli())
                .build();

        return userDocRepository.save(userDoc);  // ✅ Saves to MongoDB
    }

    // ✅ Get signed URL - Retrieves from MongoDB, generates signed URL from Cloudinary
    public String getDocumentSignedUrl(String userId, DocType docType) throws Exception {
        // 1. Get document metadata from MongoDB
        Optional<UserDoc> docOptional = userDocRepository.findByUserIdAndDocType(userId, docType);

        if (docOptional.isEmpty()) {
            throw new IllegalArgumentException("Document not found for user: " + userId + " and type: " + docType);
        }

        UserDoc doc = docOptional.get();
        String fileUrl = doc.getFileUrl();  // Get Cloudinary URL from MongoDB

        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new IllegalStateException("Document URL is empty");
        }

        // 2. Generate signed URL from Cloudinary
        String publicId = cloudinaryService.extractPublicId(fileUrl);
        return cloudinaryService.getSignedUrl(publicId);
    }

    // ✅ Get all documents for a user - From MongoDB
    public List<UserDoc> getUserDocuments(String userId) {
        return userDocRepository.findByUserId(userId);  // ✅ Queries MongoDB
    }

    // ✅ Get specific document - From MongoDB
    public Optional<UserDoc> getDocument(String userId, DocType docType) {
        return userDocRepository.findByUserIdAndDocType(userId, docType);  // ✅ Queries MongoDB
    }

    // ✅ Delete document - From Cloudinary AND MongoDB
    public void deleteDocument(String userId, DocType docType) throws Exception {
        // 1. Get document metadata from MongoDB
        Optional<UserDoc> docOptional = userDocRepository.findByUserIdAndDocType(userId, docType);

        if (docOptional.isEmpty()) {
            throw new IllegalArgumentException("Document not found");
        }

        UserDoc doc = docOptional.get();

        // 2. Delete from Cloudinary
        if (doc.getFileUrl() != null && doc.getFileUrl().contains("cloudinary")) {
            try {
                String publicId = cloudinaryService.extractPublicId(doc.getFileUrl());
                cloudinaryService.deleteFile(publicId);
            } catch (Exception e) {
                System.err.println("Failed to delete from Cloudinary: " + e.getMessage());
            }
        }

        // 3. Delete from MongoDB
        userDocRepository.delete(doc);  // ✅ Deletes from MongoDB
    }
}